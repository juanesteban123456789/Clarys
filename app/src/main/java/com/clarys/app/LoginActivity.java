package com.clarys.app;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;

import com.clarys.app.data.StoreCallback;
import com.clarys.app.data.SupabaseStore;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;

import java.util.Locale;

public class LoginActivity extends BaseScreenActivity {
    private SupabaseStore store;
    private GoogleSignInClient googleSignInClient;
    private ActivityResultLauncher<Intent> googleSignInLauncher;
    private EditText emailInput;
    private EditText passwordInput;
    private boolean registeringWithGoogle;
    private String pendingWorkshopName = "";
    private String pendingWorkshopWhatsapp = "";
    private final CountryOption[] countryOptions = new CountryOption[]{
            new CountryOption("CO", "Colombia", "+57"),
            new CountryOption("US", "Estados Unidos", "+1"),
            new CountryOption("MX", "Mexico", "+52"),
            new CountryOption("PE", "Peru", "+51"),
            new CountryOption("EC", "Ecuador", "+593"),
            new CountryOption("VE", "Venezuela", "+58"),
            new CountryOption("CL", "Chile", "+56"),
            new CountryOption("AR", "Argentina", "+54"),
            new CountryOption("BR", "Brasil", "+55"),
            new CountryOption("ES", "Espana", "+34")
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        store = SupabaseStore.getInstance(this);
        setupGoogleSignIn();
        hideHeaderShortcuts();

        emailInput = findViewById(R.id.inputLoginEmail);
        passwordInput = findViewById(R.id.inputLoginPassword);

        Button loginButton = findViewById(R.id.buttonLogin);
        Button googleButton = findViewById(R.id.buttonGoogleLogin);
        Button clientButton = findViewById(R.id.buttonEnterAsClient);
        Button registerButton = findViewById(R.id.buttonRegisterAdmin);

        loginButton.setOnClickListener(view -> signIn());
        googleButton.setOnClickListener(view -> startGoogleFlow(false, "", ""));
        clientButton.setOnClickListener(view -> openClientCatalog());
        registerButton.setOnClickListener(view -> showRegisterOptions());
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

    private void setupGoogleSignIn() {
        googleSignInLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> handleGoogleResult(result.getData()));

        if (BuildConfig.GOOGLE_WEB_CLIENT_ID.trim().isEmpty()) {
            return;
        }

        GoogleSignInOptions options = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(BuildConfig.GOOGLE_WEB_CLIENT_ID)
                .requestEmail()
                .build();
        googleSignInClient = GoogleSignIn.getClient(this, options);
    }

    private void signIn() {
        String email = emailInput.getText().toString().trim();
        String password = passwordInput.getText().toString();
        if (email.isEmpty() || password.isEmpty()) {
            showMessage("Escribe correo y contrasena");
            return;
        }

        store.signIn(email, password, new StoreCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                showMessage("Sesion iniciada");
                openScreen(MainActivity.class);
                finish();
            }

            @Override
            public void onError(String message) {
                showMessage(message);
            }
        });
    }

    private void showRegisterOptions() {
        new AlertDialog.Builder(this)
                .setTitle("Registrar taller")
                .setItems(new String[]{"Con correo y contrasena", "Con Google"}, (dialog, which) -> {
                    if (which == 0) {
                        showEmailRegisterDialog();
                    } else {
                        showGoogleRegisterDialog();
                    }
                })
                .show();
    }

    private void showEmailRegisterDialog() {
        LinearLayout form = buildDialogForm();
        EditText email = addDialogInput(form, "Correo", InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        EditText password = addDialogInput(form, "Contrasena", InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        EditText workshop = addDialogInput(form, "Nombre del taller", InputType.TYPE_CLASS_TEXT);
        Spinner country = addCountrySpinner(form);
        EditText whatsapp = addDialogInput(form, "WhatsApp del taller", InputType.TYPE_CLASS_PHONE);

        new AlertDialog.Builder(this)
                .setTitle("Registro con correo")
                .setView(form)
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Registrar", (dialog, which) ->
                        registerWithEmail(email, password, workshop, country, whatsapp))
                .show();
    }

    private void registerWithEmail(EditText email, EditText password, EditText workshop, Spinner country,
            EditText whatsapp) {
        String emailValue = email.getText().toString().trim();
        String passwordValue = password.getText().toString();
        if (emailValue.isEmpty() || passwordValue.isEmpty()) {
            showMessage("Correo y contrasena son obligatorios");
            return;
        }

        store.signUpAdmin(emailValue, passwordValue, workshop.getText().toString(),
                buildWhatsapp(country, whatsapp),
                new StoreCallback<Void>() {
                    @Override
                    public void onSuccess(Void result) {
                        showMessage("Administrador registrado");
                        openScreen(MainActivity.class);
                        finish();
                    }

                    @Override
                    public void onError(String message) {
                        showMessage(message);
                    }
                });
    }

    private void showGoogleRegisterDialog() {
        LinearLayout form = buildDialogForm();
        EditText workshop = addDialogInput(form, "Nombre del taller", InputType.TYPE_CLASS_TEXT);
        Spinner country = addCountrySpinner(form);
        EditText whatsapp = addDialogInput(form, "WhatsApp del taller", InputType.TYPE_CLASS_PHONE);

        new AlertDialog.Builder(this)
                .setTitle("Registro con Google")
                .setView(form)
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Continuar", (dialog, which) ->
                        startGoogleFlow(true, workshop.getText().toString(), buildWhatsapp(country, whatsapp)))
                .show();
    }

    private LinearLayout buildDialogForm() {
        LinearLayout form = new LinearLayout(this);
        int padding = getResources().getDimensionPixelSize(R.dimen.login_dialog_padding);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(padding, padding / 2, padding, 0);
        return form;
    }

    private EditText addDialogInput(LinearLayout form, String hint, int inputType) {
        EditText input = new EditText(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 10, 0, 0);
        input.setLayoutParams(params);
        input.setHint(hint);
        input.setInputType(inputType);
        input.setSingleLine(true);
        form.addView(input);
        return input;
    }

    private Spinner addCountrySpinner(LinearLayout form) {
        TextView label = new TextView(this);
        label.setText("Pais del WhatsApp");
        label.setTextColor(getColor(R.color.clarys_muted));
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        labelParams.setMargins(0, 14, 0, 0);
        label.setLayoutParams(labelParams);
        form.addView(label);

        Spinner spinner = new Spinner(this);
        ArrayAdapter<CountryOption> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, countryOptions);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setSelection(defaultCountryIndex());

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 6, 0, 0);
        spinner.setLayoutParams(params);
        form.addView(spinner);
        return spinner;
    }

    private int defaultCountryIndex() {
        String countryCode = Locale.getDefault().getCountry();
        if (countryCode == null || countryCode.trim().isEmpty()) {
            countryCode = "CO";
        }

        for (int i = 0; i < countryOptions.length; i++) {
            if (countryOptions[i].isoCode.equalsIgnoreCase(countryCode)) {
                return i;
            }
        }
        return 0;
    }

    private String buildWhatsapp(Spinner country, EditText whatsapp) {
        CountryOption option = (CountryOption) country.getSelectedItem();
        String rawPhone = whatsapp.getText().toString().replace(" ", "").replace("-", "").trim();
        if (rawPhone.isEmpty()) {
            return "";
        }
        if (rawPhone.startsWith("+")) {
            return rawPhone;
        }
        return option.dialCode + rawPhone;
    }

    private void startGoogleFlow(boolean register, String workshopName, String whatsapp) {
        if (googleSignInClient == null) {
            showMessage("Google requiere GOOGLE_WEB_CLIENT_ID en local.properties");
            return;
        }

        registeringWithGoogle = register;
        pendingWorkshopName = workshopName;
        pendingWorkshopWhatsapp = whatsapp;
        googleSignInClient.signOut().addOnCompleteListener(task ->
                googleSignInLauncher.launch(googleSignInClient.getSignInIntent()));
    }

    private void openClientCatalog() {
        Intent intent = new Intent(this, CatalogActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
    }

    private void handleGoogleResult(Intent data) {
        Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
        try {
            GoogleSignInAccount account = task.getResult(ApiException.class);
            String idToken = account == null ? null : account.getIdToken();
            if (idToken == null || idToken.trim().isEmpty()) {
                showMessage("Google no entrego el token de acceso");
                return;
            }

            if (registeringWithGoogle) {
                registerWithGoogle(idToken);
            } else {
                loginWithGoogle(idToken);
            }
        } catch (ApiException exception) {
            showMessage("No se pudo iniciar sesion con Google");
        }
    }

    private void loginWithGoogle(String idToken) {
        store.signInWithGoogle(idToken, new StoreCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                showMessage("Sesion iniciada con Google");
                openScreen(MainActivity.class);
                finish();
            }

            @Override
            public void onError(String message) {
                showMessage(message);
            }
        });
    }

    private void registerWithGoogle(String idToken) {
        store.signUpAdminWithGoogle(idToken, pendingWorkshopName, pendingWorkshopWhatsapp,
                new StoreCallback<Void>() {
                    @Override
                    public void onSuccess(Void result) {
                        showMessage("Taller registrado con Google");
                        openScreen(MainActivity.class);
                        finish();
                    }

                    @Override
                    public void onError(String message) {
                        showMessage(message);
                    }
                });
    }

    private static class CountryOption {
        private final String isoCode;
        private final String name;
        private final String dialCode;

        CountryOption(String isoCode, String name, String dialCode) {
            this.isoCode = isoCode;
            this.name = name;
            this.dialCode = dialCode;
        }

        @Override
        public String toString() {
            return name + " (" + dialCode + ")";
        }
    }
}
