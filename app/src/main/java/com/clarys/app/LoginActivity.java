package com.clarys.app;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import com.clarys.app.data.SupabaseStore;

public class LoginActivity extends BaseScreenActivity {

    private SupabaseStore store;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_login);

        store = SupabaseStore.getInstance(this);

        hideHeaderShortcuts();


        View clientButton =
                findViewById(R.id.buttonEnterAsClient);

        View adminButton =
                findViewById(R.id.buttonAdminAccess);


        // =========================================================
        // ENTRAR COMO CLIENTE
        // =========================================================

        clientButton.setOnClickListener(view ->
                openClientCatalog()
        );


        // =========================================================
        // ENTRAR COMO ADMINISTRADOR
        // =========================================================

        adminButton.setOnClickListener(view ->
                openAdminAccess()
        );
    }


    // =============================================================
    // PANTALLA PÚBLICA
    // =============================================================

    @Override
    protected boolean isPublicScreen() {
        return true;
    }


    // =============================================================
    // CONTEXTO
    // =============================================================

    @Override
    protected boolean isAdminContext() {

        /*
         * LoginActivity no pertenece todavía al modo
         * administrativo.
         *
         * El modo ADMIN solamente debe activarse después
         * de que AdminAccessActivity autentique correctamente.
         */
        return false;
    }


    // =============================================================
    // OCULTAR ACCESOS DEL HEADER
    // =============================================================

    private void hideHeaderShortcuts() {

        View home =
                findViewById(R.id.buttonHeaderHome);

        View back =
                findViewById(R.id.buttonHeaderBack);


        if (home != null) {
            home.setVisibility(View.GONE);
        }


        if (back != null) {
            back.setVisibility(View.GONE);
        }
    }


    // =============================================================
    // CLIENTE
    // =============================================================

    private void openClientCatalog() {

        /*
         * Entrar como cliente NO cierra necesariamente una
         * sesión administrativa existente.
         *
         * Solamente cambia el modo visual de la aplicación.
         */
        AppModeManager.enterClientMode(this);


        Intent intent =
                new Intent(
                        this,
                        CatalogActivity.class
                );


        intent.putExtra(
                "adminCatalog",
                false
        );


        /*
         * Evitamos poder pulsar atrás y volver a pantallas
         * anteriores relacionadas con administración/login.
         */
        intent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TASK
        );


        startActivity(intent);

        finish();
    }


    // =============================================================
    // ADMINISTRACIÓN
    // =============================================================

    private void openAdminAccess() {

        /*
         * Si ya existe una sesión administrativa válida,
         * podemos volver directamente al panel sin pedir
         * contraseña otra vez.
         */
        if (store.isAuthenticated()) {

            AppModeManager.enterAdminMode(this);

            Intent intent =
                    new Intent(
                            this,
                            MainActivity.class
                    );


            intent.addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK
                            | Intent.FLAG_ACTIVITY_CLEAR_TASK
            );


            startActivity(intent);

            finish();

            return;
        }


        /*
         * IMPORTANTE:
         *
         * NO hacemos:
         *
         * AppModeManager.enterAdminMode(this);
         *
         * todavía.
         *
         * AdminAccessActivity deberá activar ADMIN solamente
         * después de que el login sea exitoso.
         */
        Intent intent =
                new Intent(
                        this,
                        AdminAccessActivity.class
                );

        startActivity(intent);
    }
}