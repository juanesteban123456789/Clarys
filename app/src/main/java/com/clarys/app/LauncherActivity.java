package com.clarys.app;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.clarys.app.data.SupabaseStore;

public class LauncherActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SupabaseStore store =
                SupabaseStore.getInstance(this);


        boolean wantsAdmin =
                AppModeManager.isAdminMode(this);

        boolean authenticated =
                store.isAuthenticated();


        Intent intent;


        // =============================================
        // ADMINISTRADOR
        // =============================================

        if (wantsAdmin && authenticated) {

            intent = new Intent(
                    this,
                    MainActivity.class
            );

        } else {

            // =============================================
            // CLIENTE
            // =============================================

            // Si quedó ADMIN almacenado pero la sesión
            // expiró, volver a CLIENTE automáticamente.
            AppModeManager.enterClientMode(this);

            intent = new Intent(
                    this,
                    CatalogActivity.class
            );

            intent.putExtra(
                    "adminCatalog",
                    false
            );
        }


        startActivity(intent);

        finish();
    }
}