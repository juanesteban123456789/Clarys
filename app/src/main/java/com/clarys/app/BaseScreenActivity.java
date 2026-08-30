package com.clarys.app;

import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.IdRes;
import androidx.appcompat.app.AppCompatActivity;

import com.clarys.app.data.SupabaseStore;
import com.clarys.app.data.StoreCallback;

public abstract class BaseScreenActivity extends AppCompatActivity {

    private static final long SESSION_CHECK_INTERVAL_MILLIS = 2L * 60L * 1000L;
    private final Handler sessionHandler = new Handler(Looper.getMainLooper());
    private boolean sessionCheckInProgress;
    private boolean sessionMonitoringActive;
    private boolean redirectingToLogin;
    private final Runnable sessionCheck = this::validateAdminSession;

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

        Intent intent = new Intent(this, LoginActivity.class);
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

            homeButton.setOnClickListener(view -> {

                SupabaseStore store =
                        SupabaseStore.getInstance(this);

                boolean adminMode =
                        AppModeManager.isAdminMode(this)
                                && store.isAuthenticated();


                if (adminMode) {

                    openScreen(MainActivity.class);

                } else {

                    Intent intent =
                            new Intent(
                                    this,
                                    CatalogActivity.class
                            );

                    intent.putExtra(
                            "adminCatalog",
                            false
                    );

                    startActivity(intent);
                }
            });
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
