package com.clarys.app;

import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.IdRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;
import androidx.activity.OnBackPressedCallback;

import com.clarys.app.data.SupabaseStore;
import com.clarys.app.data.StoreCallback;

/**
 * Controlador base de navegación y acceso. Protege las pantallas privadas y
 * verifica periódicamente que la sesión administrativa permanezca vigente.
 */
public abstract class BaseScreenActivity extends AppCompatActivity {

    private static final long SESSION_CHECK_INTERVAL_MILLIS = 2L * 60L * 1000L;
    private final Handler sessionHandler = new Handler(Looper.getMainLooper());
    private boolean sessionCheckInProgress;
    private boolean sessionMonitoringActive;
    private boolean redirectingToLogin;
    private final Runnable sessionCheck = this::validateAdminSession;
    private AlertDialog exitDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() { handleBackNavigation(); }
        });
    }

    /** Atras nunca recorre pantallas antiguas: vuelve al inicio o confirma la salida. */
    protected void handleBackNavigation() {
        if (getClass().equals(homeActivity())) { confirmRootExit(); }
        else { navigateHome(); }
    }

    protected void navigateHome() {
        if (isFinishing() || isDestroyed()) { return; }
        if (getClass().equals(homeActivity())) { return; }
        requestLeaveScreen(() -> {
            Class<?> destination = homeActivity();
            if (destination == MainActivity.class) { AppModeManager.enterAdminMode(this); }
            Intent intent = new Intent(this, destination);
            intent.putExtra(CatalogActivity.EXTRA_ADMIN_CATALOG, false);
            intent.putExtra(CatalogActivity.EXTRA_OPENED_FROM_ADMIN, false);
            // El inicio es la unica raiz: no queda ningun producto o formulario debajo.
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });
    }

    /** Los formularios pueden confirmar el descarte antes de aplicar la navegacion comun. */
    protected void requestLeaveScreen(Runnable navigation) { navigation.run(); }

    private Class<?> homeActivity() {
        if (!BuildConfig.IS_ADMIN_APP) { return CatalogActivity.class; }
        return SupabaseStore.getInstance(this).isAuthenticated() ? MainActivity.class : AdminAccessActivity.class;
    }

    protected void confirmRootExit() {
        if (isFinishing() || isDestroyed()) { return; }
        if (exitDialog != null && exitDialog.isShowing()) { return; }
        boolean logout = BuildConfig.IS_ADMIN_APP && SupabaseStore.getInstance(this).isAuthenticated();
        exitDialog = new AlertDialog.Builder(this)
                .setTitle(logout ? R.string.navigation_logout_title : R.string.navigation_exit_title)
                .setMessage(logout ? R.string.navigation_logout_message : R.string.navigation_exit_message)
                .setNegativeButton(R.string.navigation_stay, null)
                .setPositiveButton(logout ? R.string.navigation_logout : R.string.navigation_exit, (dialog, which) -> {
                    if (logout) {
                        com.clarys.app.data.OrderNotifications.cancel(this);
                        SupabaseStore.getInstance(this).signOut();
                        AppModeManager.enterClientMode(this);
                        Intent intent = new Intent(this, AdminAccessActivity.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        finish();
                    } else {
                        // El catalogo no tiene sesion; salir no borra carrito, favoritos o seguimiento.
                        finishAndRemoveTask();
                    }
                }).show();
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (isPublicScreen()) {
            return;
        }

        sessionMonitoringActive = true;
        sessionHandler.removeCallbacks(sessionCheck);
        sessionHandler.post(sessionCheck);
    }

    @Override
    protected void onPause() {
        sessionMonitoringActive = false;
        sessionHandler.removeCallbacks(sessionCheck);
        super.onPause();
    }

    @Override protected void onDestroy() {
        sessionHandler.removeCallbacks(sessionCheck);
        if (exitDialog != null) { exitDialog.dismiss(); }
        super.onDestroy();
    }

    private void validateAdminSession() {
        if (sessionCheckInProgress || redirectingToLogin || isFinishing()) {
            return;
        }

        SupabaseStore store = SupabaseStore.getInstance(this);
        if (!store.isAuthenticated()) {
            closeExpiredSession();
            return;
        }

        sessionCheckInProgress = true;
        store.validateAdminSession(false, new StoreCallback<Boolean>() {
            @Override
            public void onSuccess(Boolean active) {
                sessionCheckInProgress = false;
                if (!sessionMonitoringActive) {
                    return;
                }
                if (!Boolean.TRUE.equals(active)) {
                    closeExpiredSession();
                    return;
                }
                scheduleNextSessionCheck();
            }

            @Override
            public void onError(String message) {
                sessionCheckInProgress = false;
                if (!sessionMonitoringActive) {
                    return;
                }
                // Un fallo temporal de red no debe cerrar una sesión válida.
                scheduleNextSessionCheck();
            }
        });
    }

    private void scheduleNextSessionCheck() {
        if (!sessionMonitoringActive) {
            return;
        }
        sessionHandler.removeCallbacks(sessionCheck);
        sessionHandler.postDelayed(sessionCheck, SESSION_CHECK_INTERVAL_MILLIS);
    }

    private void closeExpiredSession() {
        if (redirectingToLogin) {
            return;
        }

        redirectingToLogin = true;
        SupabaseStore.getInstance(this).signOut();
        AppModeManager.enterClientMode(this);
        showMessage("Tu sesión administrativa venció. Inicia sesión nuevamente.");

        Class<?> destination = BuildConfig.IS_ADMIN_APP
                ? AdminAccessActivity.class
                : CatalogActivity.class;
        Intent intent = new Intent(this, destination);

        if (!BuildConfig.IS_ADMIN_APP) {
            intent.putExtra("adminCatalog", false);
        }

        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
        finish();
    }


    protected void setupHeader(
            @IdRes int homeButtonId,
            @IdRes int backButtonId) {

        View homeButton =
                findViewById(homeButtonId);

        View backButton =
                findViewById(backButtonId);


        if (homeButton != null) {

            homeButton.setOnClickListener(view -> navigateHome());
        }


        if (backButton != null) {

            backButton.setOnClickListener(view ->
                    getOnBackPressedDispatcher()
                            .onBackPressed()
            );
        }
    }


    protected void bindNavigation(
            @IdRes int viewId,
            Class<?> targetActivity) {

        View view = findViewById(viewId);

        if (view != null) {

            view.setOnClickListener(
                    clickedView ->
                            openScreen(targetActivity)
            );
        }
    }


    protected void bindMessage(
            @IdRes int viewId,
            String message) {

        View view = findViewById(viewId);

        if (view != null) {

            view.setOnClickListener(
                    clickedView ->
                            showMessage(message)
            );
        }
    }


    protected void openScreen(
            Class<?> targetActivity) {

        if (isFinishing() || isDestroyed()) { return; }

        if (targetActivity == homeActivity()) { navigateHome(); return; }

        if (getClass().equals(targetActivity)) {
            return;
        }

        startActivity(
                new Intent(
                        this,
                        targetActivity
                )
        );
    }


    protected void showMessage(
            String message) {

        Toast.makeText(
                this,
                message,
                Toast.LENGTH_SHORT
        ).show();
    }


    /**
     * Determina si la pantalla requiere autenticación.
     */
    protected boolean isPublicScreen() {
        return false;
    }


    /**
     * Determina si la pantalla se está mostrando
     * dentro del contexto administrativo.
     *
     * Por defecto, una pantalla privada pertenece
     * al contexto administrativo.
     */
    protected boolean isAdminContext() {
        return !isPublicScreen();
    }
}
