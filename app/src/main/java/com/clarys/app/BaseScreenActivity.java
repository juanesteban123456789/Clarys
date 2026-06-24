package com.clarys.app;

import android.content.Intent;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.IdRes;
import androidx.appcompat.app.AppCompatActivity;

public abstract class BaseScreenActivity extends AppCompatActivity {

    protected void setupHeader(@IdRes int homeButtonId, @IdRes int backButtonId) {
        View homeButton = findViewById(homeButtonId);
        View backButton = findViewById(backButtonId);

        if (homeButton != null) {
            homeButton.setOnClickListener(view -> openScreen(MainActivity.class));
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
}
