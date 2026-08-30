package com.clarys.app;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.clarys.app.data.SupabaseStore;
import com.clarys.app.data.StoreCallback;

public class LauncherActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SupabaseStore store =
                SupabaseStore.getInstance(this);


        boolean wantsAdmin =
                AppModeManager.isAdminMode(this);

        if (!wantsAdmin) {
            openClientCatalog();
            return;
        }

        if (!store.isAuthenticated()) {
            openLogin();
            return;
        }

        store.validateAdminSession(true, new StoreCallback<Boolean>() {
            @Override
            public void onSuccess(Boolean active) {
                if (Boolean.TRUE.equals(active)) {
                    openAndFinish(MainActivity.class);
                } else {
                    store.signOut();
                    AppModeManager.enterClientMode(LauncherActivity.this);
                    openLogin();
                }
            }

            @Override
            public void onError(String message) {
                // Si no hay red, se conserva la sesión local y las peticiones
                // volverán a intentar la renovación cuando exista conexión.
                openAndFinish(MainActivity.class);
            }
        });
    }

    private void openClientCatalog() {
        Intent intent = new Intent(this, CatalogActivity.class);
        intent.putExtra("adminCatalog", false);
        startActivity(intent);
        finish();
    }

    private void openLogin() {
        openAndFinish(LoginActivity.class);
    }

    private void openAndFinish(Class<?> targetActivity) {
        startActivity(new Intent(this, targetActivity));
        finish();
    }
}
