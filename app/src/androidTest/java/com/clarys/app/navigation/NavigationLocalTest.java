package com.clarys.app.navigation;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.widget.EditText;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.clarys.app.AdminAccessActivity;
import com.clarys.app.AppModeManager;
import com.clarys.app.BaseScreenActivity;
import com.clarys.app.BuildConfig;
import com.clarys.app.CartActivity;
import com.clarys.app.CatalogActivity;
import com.clarys.app.ClientOrdersActivity;
import com.clarys.app.HelpActivity;
import com.clarys.app.MainActivity;
import com.clarys.app.ProductFormActivity;
import com.clarys.app.ProductListActivity;
import com.clarys.app.R;
import com.clarys.app.data.SupabaseSession;
import java.util.ArrayList;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static androidx.test.espresso.matcher.RootMatchers.isDialog;

/** Solo emulador sin red y sesion sintetica; no usa cuentas ni modifica datos reales. */
@RunWith(AndroidJUnit4.class)
public class NavigationLocalTest {
    private Instrumentation instrumentation;
    private Context context;
    private SupabaseSession session;
    private final List<Activity> opened = new ArrayList<>();

    @Before public void isolated() {
        assumeTrue(Boolean.parseBoolean(InstrumentationRegistry.getArguments().getString("clarysNavigationTest", "false")));
        assertTrue("Solo emulador", "ranchu".equals(Build.HARDWARE) || "goldfish".equals(Build.HARDWARE));
        instrumentation = InstrumentationRegistry.getInstrumentation();
        context = instrumentation.getTargetContext();
        assertEquals(1, android.provider.Settings.Global.getInt(context.getContentResolver(),
                android.provider.Settings.Global.AIRPLANE_MODE_ON, 0));
        session = SupabaseSession.getInstance(context);
        session.clear();
        if (BuildConfig.IS_ADMIN_APP) {
            assertTrue(session.saveAuth("LOCAL_NAV_NOT_VALID", "LOCAL_NAV_NOT_REFRESH", "local-navigation",
                    System.currentTimeMillis() / 1000 + 3600));
            session.saveProfile("local-navigation-workshop", "admin");
            AppModeManager.enterAdminMode(context);
        } else { AppModeManager.enterClientMode(context); }
    }
    @After public void cleanup() {
        if (instrumentation == null) { return; }
        instrumentation.runOnMainSync(() -> { for (Activity screen : opened) { if (!screen.isDestroyed()) { screen.finish(); } } });
        instrumentation.waitForIdleSync();
        session.clear();
    }
    private Class<? extends Activity> homeClass() { return BuildConfig.IS_ADMIN_APP ? MainActivity.class : CatalogActivity.class; }
    private Activity start(Class<? extends Activity> type) {
        Activity screen = instrumentation.startActivitySync(new Intent(context, type).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        opened.add(screen); instrumentation.waitForIdleSync(); return screen;
    }
    private Activity arrives(Class<? extends Activity> target, Runnable action) {
        return arrives(target, action, true);
    }
    private Activity arrives(Class<? extends Activity> target, Runnable action, boolean onMainThread) {
        Instrumentation.ActivityMonitor monitor = instrumentation.addMonitor(target.getName(), null, false);
        try {
            if (onMainThread) { instrumentation.runOnMainSync(action); } else { action.run(); }
            Activity screen = monitor.waitForActivityWithTimeout(10000);
            assertNotNull("Debe abrirse " + target.getSimpleName(), screen);
            opened.add(screen); instrumentation.waitForIdleSync(); return screen;
        } finally { instrumentation.removeMonitor(monitor); }
    }
    private void back(Activity screen) {
        // Esperar a recuperar el foco despues de cerrar un dialogo, como un toque real.
        java.util.concurrent.atomic.AtomicBoolean focused = new java.util.concurrent.atomic.AtomicBoolean();
        for (int i = 0; i < 100 && !focused.get(); i++) {
            instrumentation.runOnMainSync(() -> focused.set(screen.hasWindowFocus()));
            if (!focused.get()) { android.os.SystemClock.sleep(50); }
        }
        assertTrue("La pantalla debe tener foco antes de pulsar Atras", focused.get());
        instrumentation.runOnMainSync(() -> ((BaseScreenActivity) screen).getOnBackPressedDispatcher().onBackPressed());
        instrumentation.waitForIdleSync();
    }
    @Test public void homeRemovesOldScreensAndRootBackConfirmsExit() {
        Activity first = start(BuildConfig.IS_ADMIN_APP ? ProductListActivity.class : ClientOrdersActivity.class);
        Activity cart = arrives(CartActivity.class, () -> first.startActivity(new Intent(first, CartActivity.class)));
        Activity home = arrives(homeClass(), () -> cart.findViewById(R.id.buttonHeaderHome).performClick());
        assertTrue("El inicio no debe tener productos o carrito debajo", home.isTaskRoot());
        assertTrue(first.isFinishing() || first.isDestroyed());
        back(home);
        onView(withText(BuildConfig.IS_ADMIN_APP ? R.string.navigation_logout_title : R.string.navigation_exit_title))
                .inRoot(isDialog()).check(matches(isDisplayed()));
        onView(withId(android.R.id.button2)).inRoot(isDialog()).perform(click());
        assertFalse(home.isFinishing());
        if (BuildConfig.IS_ADMIN_APP) {
            assertTrue(session.isAuthenticated());
            back(home);
            Activity login = arrives(AdminAccessActivity.class, () -> onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click()), false);
            assertFalse(session.isAuthenticated());
            assertTrue(login.isTaskRoot());
            back(login);
            onView(withText(R.string.navigation_exit_title)).inRoot(isDialog()).check(matches(isDisplayed()));
            onView(withId(android.R.id.button2)).inRoot(isDialog()).perform(click());
            assertFalse(session.isAuthenticated());
        } else {
            back(home);
            onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click());
            assertTrue(home.isFinishing() || home.isDestroyed());
        }
    }
    @Test public void toolbarBackAndAndroidBackBothReturnHome() {
        Activity orders = start(ClientOrdersActivity.class);
        Activity home = arrives(homeClass(), () -> orders.findViewById(R.id.buttonHeaderBack).performClick());
        assertTrue(home.isTaskRoot());
        Activity help = arrives(HelpActivity.class, () -> home.startActivity(new Intent(home, HelpActivity.class)));
        Activity restored = arrives(homeClass(), () -> ((BaseScreenActivity) help).getOnBackPressedDispatcher().onBackPressed());
        assertTrue(restored.isTaskRoot());
    }
    @Test public void productFormStillConfirmsDiscardBeforeReturningHome() {
        assumeTrue(BuildConfig.IS_ADMIN_APP);
        Activity form = start(ProductFormActivity.class);
        instrumentation.runOnMainSync(() -> ((EditText) form.findViewById(R.id.inputProductName)).setText("Producto local sin guardar"));
        back(form);
        onView(withText("¿Descartar cambios?")).inRoot(isDialog()).check(matches(isDisplayed()));
        onView(withId(android.R.id.button2)).inRoot(isDialog()).perform(click());
        assertFalse(form.isFinishing());
        back(form);
        Activity home = arrives(MainActivity.class, () -> onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click()), false);
        assertTrue(home.isTaskRoot());
    }
    @Test public void adminCatalogPreviewReturnsToPanelWithoutLoggingOut() {
        assumeTrue(BuildConfig.IS_ADMIN_APP);
        Activity catalog = instrumentation.startActivitySync(new Intent(context, CatalogActivity.class)
                .putExtra(CatalogActivity.EXTRA_OPENED_FROM_ADMIN, true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        opened.add(catalog);
        Activity home = arrives(MainActivity.class, () -> ((BaseScreenActivity) catalog).getOnBackPressedDispatcher().onBackPressed());
        assertTrue(home.isTaskRoot()); assertTrue(session.isAuthenticated());
    }
    @Test public void clientSearchBackReturnsToCatalogBeforeExit() {
        assumeTrue(!BuildConfig.IS_ADMIN_APP);
        Activity catalog = start(CatalogActivity.class);
        instrumentation.runOnMainSync(() -> ((EditText) catalog.findViewById(R.id.inputCatalogSearch)).setText("Búsqueda local"));
        back(catalog);
        instrumentation.runOnMainSync(() -> assertEquals("", ((EditText) catalog.findViewById(R.id.inputCatalogSearch)).getText().toString()));
        assertFalse(catalog.isFinishing());
        back(catalog);
        onView(withText(R.string.navigation_exit_title)).inRoot(isDialog()).check(matches(isDisplayed()));
        onView(withId(android.R.id.button2)).inRoot(isDialog()).perform(click());
    }
}
