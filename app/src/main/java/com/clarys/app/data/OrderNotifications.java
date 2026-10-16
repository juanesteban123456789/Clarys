package com.clarys.app.data;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.content.pm.PackageManager;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import com.clarys.app.BuildConfig;
import com.clarys.app.R;
import com.clarys.app.ReportsActivity;
import com.clarys.app.model.OrderRequest;
import java.util.List;
import java.util.Calendar;
import java.util.concurrent.TimeUnit;

/** Avisos locales de pedidos; el scheduler respeta los limites de bateria de Android. */
public final class OrderNotifications {
    private static final String NEW_ORDERS_CHANNEL = "clarys_new_orders";
    private static final String DAILY_CHANNEL = "clarys_orders";
    private static final int JOB_ID=41013;
    private static final int NEW_ORDERS_NOTIFICATION_ID = 41014;
    private static final int DAILY_NOTIFICATION_ID = 41015;
    private OrderNotifications() { }

    public static boolean isEnabled(Context context) {
        return preferences(context).getBoolean("enabled",true);
    }
    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences("clarys_order_alerts",Context.MODE_PRIVATE);
    }
    public static void setEnabled(Context context,boolean enabled) {
        preferences(context).edit().putBoolean("enabled",enabled).apply();
        if(enabled) { schedule(context); } else { cancel(context); }
    }
    public static boolean isDailyReminderEnabled(Context context) {
        return preferences(context).getBoolean("daily_reminder_enabled", true);
    }
    public static void setDailyReminderEnabled(Context context, boolean enabled) {
        preferences(context).edit().putBoolean("daily_reminder_enabled", enabled).apply();
        if (enabled) {
            schedule(context);
        } else {
            context.getSystemService(NotificationManager.class).cancel(DAILY_NOTIFICATION_ID);
        }
    }
    public static void schedule(Context context) {
        if(!BuildConfig.IS_ADMIN_APP || !isEnabled(context)) { return; }
        createChannels(context);
        JobScheduler scheduler=context.getSystemService(JobScheduler.class);
        if(scheduler.getPendingJob(JOB_ID)!=null) { return; }
        scheduler.schedule(new JobInfo.Builder(JOB_ID,new ComponentName(context,OrderNotificationJobService.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPeriodic(TimeUnit.MINUTES.toMillis(15))
                .setPersisted(true).build());
    }
    public static void cancel(Context context) {
        context.getSystemService(JobScheduler.class).cancel(JOB_ID);
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        manager.cancel(NEW_ORDERS_NOTIFICATION_ID);
        manager.cancel(DAILY_NOTIFICATION_ID);
    }

    private static void createChannels(Context context) {
        if (Build.VERSION.SDK_INT < 26) { return; }
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        NotificationChannel previous = manager.getNotificationChannel(DAILY_CHANNEL);
        // Un canal existente no puede elevarse a HIGH. El aviso emergente usa otro canal,
        // pero conserva el bloqueo si el usuario habia desactivado los avisos anteriores.
        int importance = previous != null && previous.getImportance() == NotificationManager.IMPORTANCE_NONE
                ? NotificationManager.IMPORTANCE_NONE : NotificationManager.IMPORTANCE_HIGH;
        NotificationChannel newOrders = new NotificationChannel(NEW_ORDERS_CHANNEL,
                context.getString(R.string.order_notifications_channel_new), importance);
        newOrders.setDescription(context.getString(R.string.order_notifications_channel_new_description));
        manager.createNotificationChannel(newOrders);
        NotificationChannel daily = new NotificationChannel(DAILY_CHANNEL,
                context.getString(R.string.order_notifications_channel_daily), NotificationManager.IMPORTANCE_DEFAULT);
        daily.setDescription(context.getString(R.string.order_notifications_channel_daily_description));
        manager.createNotificationChannel(daily);
    }

    private static boolean canNotify(Context context, String channel) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) { return false; }
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) { return false; }
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel setting = context.getSystemService(NotificationManager.class).getNotificationChannel(channel);
            return setting != null && setting.getImportance() != NotificationManager.IMPORTANCE_NONE;
        }
        return true;
    }

    public static synchronized void process(Context context,String workshopId,List<OrderRequest> orders) {
        if(!BuildConfig.IS_ADMIN_APP || workshopId==null || workshopId.isEmpty() || !isEnabled(context)) { return; }
        SupabaseStore store = SupabaseStore.getInstance(context);
        if (!store.isAuthenticated() || !workshopId.equals(store.getWorkshopId())) { return; }
        createChannels(context);
        SharedPreferences prefs=preferences(context);
        String key="last_order_"+workshopId;
        int lastSeen=prefs.getInt(key,0);
        OrderNotificationPolicy.Summary summary = OrderNotificationPolicy.summarize(workshopId, orders, lastSeen);
        boolean first=!prefs.contains(key);
        boolean newPosted = false;
        if (!first && summary.getNewPending() > 0 && canNotify(context, NEW_ORDERS_CHANNEL)) {
            String text = context.getResources().getQuantityString(R.plurals.order_notifications_new_count,
                    summary.getNewPending(), summary.getNewPending());
            newPosted = post(context, NEW_ORDERS_CHANNEL, NEW_ORDERS_NOTIFICATION_ID,
                    context.getString(R.string.order_notifications_new_title), text, text,
                    OrderRequest.STATUS_PENDING, workshopId, true, false);
        }
        // No marcar como vistos los avisos pendientes si Android impidio publicarlos.
        if (first || summary.getNewPending() == 0 || newPosted) {
            prefs.edit().putInt(key, summary.getMaximumId()).apply();
        }
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (summary.getPending() == 0) { manager.cancel(NEW_ORDERS_NOTIFICATION_ID); }
        if (summary.getActiveCount() == 0) {
            manager.cancel(DAILY_NOTIFICATION_ID);
            return;
        }
        Calendar now = Calendar.getInstance();
        String dailyKey = "last_daily_reminder_" + workshopId;
        if (!isDailyReminderEnabled(context) || !canNotify(context, DAILY_CHANNEL)
                || !OrderNotificationPolicy.isDailyReminderDue(now, prefs.getString(dailyKey, ""),
                summary.getActiveCount())) { return; }
        String text = context.getResources().getQuantityString(R.plurals.order_notifications_active_count,
                summary.getActiveCount(), summary.getActiveCount());
        String detail = context.getString(R.string.order_notifications_daily_detail, summary.getPending(),
                summary.getContacted(), summary.getApproved(), summary.getPreparing());
        if (post(context, DAILY_CHANNEL, DAILY_NOTIFICATION_ID,
                context.getString(R.string.order_notifications_daily_title), text, text + "\n" + detail,
                ReportsActivity.FILTER_ACTIVE, workshopId, false, newPosted)) {
            prefs.edit().putString(dailyKey, OrderNotificationPolicy.dayKey(now)).apply();
        }
    }

    private static boolean post(Context context, String channel, int id, String title, String text,
                                String detail, String status, String workshopId, boolean headsUp, boolean silent) {
        Intent intent = new Intent(context, ReportsActivity.class);
        intent.putExtra("reportSection", "orders");
        intent.putExtra("requestStatus", status);
        intent.putExtra("notificationWorkshopId", workshopId);
        PendingIntent action = PendingIntent.getActivity(context, id, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder notification = new NotificationCompat.Builder(context, channel)
                .setSmallIcon(R.drawable.ic_store).setContentTitle(title).setContentText(text)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(detail))
                .setPriority(headsUp ? NotificationCompat.PRIORITY_HIGH : NotificationCompat.PRIORITY_DEFAULT)
                .setDefaults(NotificationCompat.DEFAULT_ALL).setSilent(silent)
                .setCategory(headsUp ? NotificationCompat.CATEGORY_EVENT : NotificationCompat.CATEGORY_REMINDER)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setAutoCancel(true).setContentIntent(action);
        try {
            context.getSystemService(NotificationManager.class).notify(id, notification.build());
            return true;
        } catch (SecurityException revokedPermission) {
            return false;
        }
    }
}
