package com.clarys.app.evidence.sqlite;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.clarys.app.BaseScreenActivity;

import com.clarys.app.R;

/**
 * Pantalla aislada para demostrar autenticación local con SQLite en la evidencia
 * GA8-220501096-AA2-EV02. No inicia sesiones administrativas ni reemplaza la
 * autenticación productiva de Clarys mediante Supabase Auth.
 */
public class SQLiteEvidenceActivity extends BaseScreenActivity {

    private static final String TAG = "SQLiteEvidence";

    private SQLiteEvidenceDatabaseHelper databaseHelper;
    private EditText emailInput;
    private EditText passwordInput;
    private TextView resultText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sqlite_evidence);

        emailInput = findViewById(R.id.inputSQLiteEvidenceEmail);
        passwordInput = findViewById(R.id.inputSQLiteEvidencePassword);
        resultText = findViewById(R.id.textSQLiteEvidenceResult);
        databaseHelper = new SQLiteEvidenceDatabaseHelper(this);

        // Fuerza la creación inicial para que CREATE TABLE e INSERT se ejecuten.
        databaseHelper.getWritableDatabase();

        findViewById(R.id.buttonSQLiteEvidenceBack)
                .setOnClickListener(view -> navigateHome());
        findViewById(R.id.buttonSQLiteEvidenceValidate)
                .setOnClickListener(view -> validateCredentials());
        findViewById(R.id.buttonSQLiteEvidenceClear)
                .setOnClickListener(view -> clearForm());
    }

    @Override protected boolean isPublicScreen() { return true; }

    /**
     * Comprueba los campos obligatorios y delega la consulta local al helper.
     * Un resultado correcto solo se informa en pantalla; no abre el panel real.
     */
    private void validateCredentials() {
        String email = emailInput.getText().toString().trim();
        String password = passwordInput.getText().toString();

        if (email.isEmpty() || password.isEmpty()) {
            showResult(
                    getString(R.string.sqlite_evidence_required_fields),
                    false);
            return;
        }

        boolean validUser = databaseHelper.validateUser(email, password);
        if (validUser) {
            showResult(getString(R.string.sqlite_evidence_success), true);
            Log.i(TAG, "Validación exitosa");
        } else {
            showResult(getString(R.string.sqlite_evidence_invalid_credentials), false);
            Log.i(TAG, "Validación fallida");
        }
    }

    /** Muestra un mensaje visual y un Toast sin exponer información sensible. */
    private void showResult(String message, boolean success) {
        resultText.setText(message);
        resultText.setTextColor(getColor(success
                ? R.color.clarys_mid
                : R.color.clarys_danger));
        resultText.setVisibility(View.VISIBLE);
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    /** Restablece la demostración para repetir los casos de prueba. */
    private void clearForm() {
        emailInput.setText("");
        passwordInput.setText("");
        emailInput.setError(null);
        passwordInput.setError(null);
        resultText.setText(R.string.sqlite_evidence_waiting);
        resultText.setTextColor(getColor(R.color.clarys_muted));
        emailInput.requestFocus();
    }

    @Override
    protected void onDestroy() {
        databaseHelper.close();
        super.onDestroy();
    }
}
