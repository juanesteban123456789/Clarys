package com.clarys.app;

import android.content.Intent;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.IdRes;
import androidx.appcompat.app.AppCompatActivity;

import com.clarys.app.data.SupabaseStore;

public abstract class BaseScreenActivity extends AppCompatActivity {
    @Override
    protected void onResume() {
        super.onResume();
        if (!isPublicScreen() && !SupabaseStore.getInstance(this).isAuthenticated()) {
            showMessage("Inicia sesión para entrar al panel administrativo");
            openScreen(LoginActivity.class);
            finish();
        }
    }

    protected void setupHeader(@IdRes int homeButtonId, @IdRes int backButtonId) {
        View homeButton = findViewById(homeButtonId);
        View backButton = findViewById(backButtonId);

        if (homeButton != null) {
            homeButton.setOnClickListener(view -> {
                Class<?> target = SupabaseStore.getInstance(this).isAuthenticated()
                        ? MainActivity.class
                        : CatalogActivity.class;
                openScreen(target);
            });
        }

        if (backButton != null) {
            backButton.setOnClickListener(view -> getOnBackPressedDispatcher().onBackPressed());
        }
    }

    protected void bindNavigation(@IdRes int viewId, Class<?> targetActivity) {
        View view = findViewById(viewId);
        if (view != null) {
            view.setOnClickListener(clickedView -> openScreen(targetActivity));
        }
    }

    protected void bindMessage(@IdRes int viewId, String message) {
        View view = findViewById(viewId);
        if (view != null) {
            view.setOnClickListener(clickedView -> showMessage(message));
        }
    }

    protected void openScreen(Class<?> targetActivity) {
        if (getClass().equals(targetActivity)) {
            return;
        }
        startActivity(new Intent(this, targetActivity));
    }

    protected void showMessage(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    protected boolean isPublicScreen() {
        return false;
    }
}
