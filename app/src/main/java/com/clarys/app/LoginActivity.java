package com.clarys.app;

import android.os.Bundle;
import android.widget.Button;

public class LoginActivity extends BaseScreenActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        setupHeader(R.id.buttonHeaderHome, R.id.buttonHeaderBack);
        bindMessage(R.id.buttonForgotPassword, "Recuperacion de contrasena pendiente de logica real");

        Button loginButton = findViewById(R.id.buttonLogin);
        loginButton.setOnClickListener(view -> {
            showMessage("Inicio de sesion de ejemplo");
            openScreen(MainActivity.class);
        });
    }
}
