package com.clarys.app.data;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Instrumentation;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.SystemClock;
import android.service.notification.StatusBarNotification;
import android.util.Log;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.clarys.app.BuildConfig;
import com.clarys.app.ReportsActivity;
import com.clarys.app.model.OrderRequest;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

/** Prueba opt-in de avisos locales: no crea pedidos, no escribe en Supabase ni cambia contadores reales. */
@RunWith(AndroidJUnit4.class)
public class OrderNotificationsDeviceTest {
    private static final String TAG = "ClarysNotificationTest";
    private static final int NEW_ID = 41014;
    private static final int DAILY_ID = 41015;
    private static final int JOB_ID = 41013;

    private OrderRequest order(int id, String workshop, String status) {
        return new OrderRequest(id, workshop, "Prueba local de notificaciones", "", Collections.emptyList(),
                status, "", "", "", "", "", "", "", "");
    }

    private StatusBarNotification find(NotificationManager manager, int id) {
        for (StatusBarNotification notification : manager.getActiveNotifications()) {
            if (notification.getId() == id && notification.getTag() == null) { return notification; }
        }
        return null;
    }

    private StatusBarNotification awaitNotification(NotificationManager manager, int id) {
        long deadline = SystemClock.uptimeMillis() + 5000;
        StatusBarNotification notification;
        do {
            notification = find(manager, id);
            if (notification != null) { return notification; }
            SystemClock.sleep(100);
        } while (SystemClock.uptimeMillis() < deadline);
        fail("Android no publico el aviso " + id);
        return null;
    }

    private boolean hasText(AccessibilityNodeInfo node, String text) {
        if (node == null) { return false; }
        if (node.isVisibleToUser() && node.getText() != null
                && node.getText().toString().contains(text)) { return true; }
        for (int index = 0; index < node.getChildCount(); index++) {
            AccessibilityNodeInfo child = node.getChild(index);
            try {
                if (hasText(child, text)) { return true; }
            } finally {
                if (child != null) { child.recycle(); }
            }
        }
        return false;
    }

    private boolean bannerVisible(Instrumentation instrumentation) {
        for (AccessibilityWindowInfo window : instrumentation.getUiAutomation().getWindows()) {
            AccessibilityNodeInfo root = window.getRoot();
            try {
                // MIUI puede atribuir los RemoteViews del banner al paquete de la app.
                if (root != null && hasText(root, "Nuevos pedidos")
                        && hasText(root, "Tienes 1 pedido nuevo por revisar.")) { return true; }
            } finally {
                if (root != null) { root.recycle(); }
                window.recycle();
            }
        }
        return false;
    }

    private void screenshot(Instrumentation instrumentation, Context context, String name) throws Exception {
        Bitmap screenshot = instrumentation.getUiAutomation().takeScreenshot();
        assertNotNull("No se pudo capturar la pantalla de prueba", screenshot);
        File directory = context.getExternalFilesDir("notification-test");
        assertNotNull(directory);
        try (FileOutputStream output = new FileOutputStream(new File(directory, name))) {
            assertTrue(screenshot.compress(Bitmap.CompressFormat.PNG, 100, output));
        } finally {
            screenshot.recycle();
        }
    }

    @Test public void newBannerDailyReminderDeduplicationAndNavigation() throws Exception {
        assumeTrue(BuildConfig.IS_ADMIN_APP && Boolean.parseBoolean(
                InstrumentationRegistry.getArguments().getString("clarysNotificationTest", "false")));
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Context real = instrumentation.getTargetContext();
        SupabaseStore store = SupabaseStore.getInstance(real);
        assertTrue("Inicia sesion como administrador antes de la prueba", store.isAuthenticated());
        String workshop = store.getWorkshopId();
        assertNotNull("La cuenta no tiene taller cargado", workshop);
        assertFalse(workshop.isEmpty());
        assertTrue("El telefono debe estar despues de las 09:00 para probar el recordatorio real",
                Calendar.getInstance().get(Calendar.HOUR_OF_DAY) >= OrderNotificationPolicy.DAILY_REMINDER_HOUR);

        // Solo este archivo de preferencias recibe el estado sintetico de la prueba.
        Context isolated = new ContextWrapper(real) {
            @Override public SharedPreferences getSharedPreferences(String name, int mode) {
                return super.getSharedPreferences("clarys_order_alerts".equals(name)
                        ? "clarys_notification_test_state" : name, mode);
            }
        };
        SharedPreferences prefs = isolated.getSharedPreferences("clarys_order_alerts", Context.MODE_PRIVATE);
        NotificationManager manager = real.getSystemService(NotificationManager.class);
        JobScheduler scheduler = real.getSystemService(JobScheduler.class);
        JobInfo previousJob = scheduler.getPendingJob(JOB_ID);
        List<StatusBarNotification> previous = Arrays.asList(manager.getActiveNotifications());
        AccessibilityServiceInfo serviceInfo = instrumentation.getUiAutomation().getServiceInfo();
        int previousFlags = serviceInfo.flags;
        serviceInfo.flags |= AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
        serviceInfo.flags |= AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS;
        instrumentation.getUiAutomation().setServiceInfo(serviceInfo);
        Instrumentation.ActivityMonitor monitor = null;
        try {
            scheduler.cancel(JOB_ID);
            manager.cancel(NEW_ID);
            manager.cancel(DAILY_ID);
            assertTrue(prefs.edit().clear().putBoolean("enabled", true)
                    .putBoolean("daily_reminder_enabled", false).commit());

            OrderNotifications.process(isolated, workshop,
                    Collections.singletonList(order(1, workshop, OrderRequest.STATUS_PENDING)));
            SystemClock.sleep(300);
            assertNull("La primera lectura no debe anunciar pedidos antiguos", find(manager, NEW_ID));
            Log.i(TAG, "PASS: primera lectura sin avisos de pedidos antiguos");

            List<OrderRequest> orders = Arrays.asList(order(1, workshop, OrderRequest.STATUS_PENDING),
                    order(2, workshop, OrderRequest.STATUS_PENDING),
                    order(3, workshop, OrderRequest.STATUS_CONTACTED),
                    order(4, workshop, OrderRequest.STATUS_APPROVED),
                    order(5, workshop, OrderRequest.STATUS_PREPARING),
                    order(6, workshop, OrderRequest.STATUS_COMPLETED),
                    order(7, workshop, OrderRequest.STATUS_REJECTED));
            OrderNotifications.process(isolated, workshop, orders);
            StatusBarNotification newOrder = awaitNotification(manager, NEW_ID);
            assertEquals("clarys_new_orders", newOrder.getNotification().getChannelId());
            assertEquals(NotificationManager.IMPORTANCE_HIGH,
                    manager.getNotificationChannel("clarys_new_orders").getImportance());
            assertEquals("Tienes 1 pedido nuevo por revisar.",
                    newOrder.getNotification().extras.getCharSequence(Notification.EXTRA_TEXT).toString());
            boolean banner = false;
            long bannerDeadline = SystemClock.uptimeMillis() + 2500;
            do {
                banner = bannerVisible(instrumentation);
                if (!banner) { SystemClock.sleep(100); }
            } while (!banner && SystemClock.uptimeMillis() < bannerDeadline);
            screenshot(instrumentation, real, "new-order-banner.png");
            Log.i(TAG, "BANNER_VISIBLE=" + banner);

            OrderNotifications.process(isolated, workshop, orders);
            SystemClock.sleep(300);
            assertEquals("El mismo pedido no debe generar otro aviso", newOrder.getPostTime(),
                    awaitNotification(manager, NEW_ID).getPostTime());
            Log.i(TAG, "PASS: aviso de pedido nuevo y deduplicacion");

            assertTrue(prefs.edit().putBoolean("daily_reminder_enabled", true).commit());
            OrderNotifications.process(isolated, workshop, orders);
            StatusBarNotification daily = awaitNotification(manager, DAILY_ID);
            assertEquals("Tienes 5 pedidos pendientes de atención.",
                    daily.getNotification().extras.getCharSequence(Notification.EXTRA_TEXT).toString());
            String detail = daily.getNotification().extras.getCharSequence(Notification.EXTRA_BIG_TEXT).toString();
            assertTrue(detail.contains("Nuevos: 2 · Contactados: 1"));
            assertTrue(detail.contains("Confirmados: 1 · En preparación: 1"));
            String dateKey = "last_daily_reminder_" + workshop;
            assertEquals(OrderNotificationPolicy.dayKey(Calendar.getInstance()), prefs.getString(dateKey, ""));
            OrderNotifications.process(isolated, workshop, orders);
            SystemClock.sleep(300);
            assertEquals("El resumen diario no debe repetirse el mismo dia", daily.getPostTime(),
                    awaitNotification(manager, DAILY_ID).getPostTime());
            Log.i(TAG, "PASS: resumen diario de 5 pedidos activos; sin entregados/cancelados; sin repeticion");

            instrumentation.getUiAutomation().executeShellCommand("cmd statusbar expand-notifications").close();
            SystemClock.sleep(900);
            screenshot(instrumentation, real, "daily-notifications.png");
            instrumentation.getUiAutomation().executeShellCommand("cmd statusbar collapse").close();
            instrumentation.getUiAutomation().adoptShellPermissionIdentity(
                    "android.permission.START_ACTIVITIES_FROM_BACKGROUND");
            monitor = instrumentation.addMonitor(ReportsActivity.class.getName(), null, false);
            daily.getNotification().contentIntent.send();
            android.app.Activity reports = monitor.waitForActivityWithTimeout(5000);
            assertNotNull("Al tocar el resumen deben abrirse los pedidos", reports);
            assertEquals(ReportsActivity.FILTER_ACTIVE, reports.getIntent().getStringExtra("requestStatus"));
            instrumentation.runOnMainSync(reports::finish);
            Log.i(TAG, "PASS: resumen abre filtro Por atender");

            OrderNotifications.process(isolated, workshop,
                    Collections.singletonList(order(8, workshop, OrderRequest.STATUS_COMPLETED)));
            SystemClock.sleep(300);
            assertNull(find(manager, NEW_ID));
            assertNull(find(manager, DAILY_ID));
            Log.i(TAG, "PASS: avisos retirados al no quedar pedidos activos");
            assertTrue("Android publico el aviso, pero no se pudo confirmar visualmente el banner", banner);
        } finally {
            if (monitor != null) { instrumentation.removeMonitor(monitor); }
            instrumentation.getUiAutomation().dropShellPermissionIdentity();
            serviceInfo.flags = previousFlags;
            instrumentation.getUiAutomation().setServiceInfo(serviceInfo);
            manager.cancel(NEW_ID);
            manager.cancel(DAILY_ID);
            for (StatusBarNotification original : previous) {
                if (original.getTag() == null && (original.getId() == NEW_ID || original.getId() == DAILY_ID)) {
                    manager.notify(original.getId(), original.getNotification());
                }
            }
            assertTrue(prefs.edit().clear().commit());
            if (previousJob != null) { scheduler.schedule(previousJob); }
        }
    }
}
