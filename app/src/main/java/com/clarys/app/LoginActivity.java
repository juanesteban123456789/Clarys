package com.clarys.app;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

public class LoginActivity extends BaseScreenActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        hideHeaderShortcuts();

        View clientButton = findViewById(R.id.buttonEnterAsClient);
        View adminButton = findViewById(R.id.buttonAdminAccess);

        clientButton.setOnClickListener(view -> openClientCatalog());
        adminButton.setOnClickListener(view -> openScreen(AdminAccessActivity.class));
    }

    @Override
    protected boolean isPublicScreen() {
        return true;
    }

    private void hideHeaderShortcuts() {
        View home = findViewById(R.id.buttonHeaderHome);
        View back = findViewById(R.id.buttonHeaderBack);
        if (home != null) {
            home.setVisibility(View.GONE);
        }
        if (back != null) {
            back.setVisibility(View.GONE);
        }
    }

    private void openClientCatalog() {
        Intent intent = new Intent(this, CatalogActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
    }
}
