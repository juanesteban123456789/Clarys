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

public class AdminAccessActivity extends BaseScreenActivity {

    private SupabaseStore store;

    private GoogleSignInClient googleSignInClient;

    private ActivityResultLauncher<Intent> googleSignInLauncher;

    private EditText emailInput;

    private EditText passwordInput;

    private boolean registeringWithGoogle;

    private String pendingWorkshopName = "";

    private String pendingWorkshopWhatsapp = "";


    private final CountryOption[] countryOptions =
            new CountryOption[]{
                    new CountryOption(
                            "CO",
                            "Colombia",
                            "+57"
                    )
            };


    // =============================================================
    // ON CREATE
    // =============================================================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(
                R.layout.activity_admin_access
        );


        store =
                SupabaseStore.getInstance(this);


        setupGoogleSignIn();


        setupHeader(
                R.id.buttonHeaderHome,
                R.id.buttonHeaderBack
        );


        emailInput =
                findViewById(
                        R.id.inputLoginEmail
                );


        passwordInput =
                findViewById(
                        R.id.inputLoginPassword
                );


        Button loginButton =
                findViewById(
                        R.id.buttonLogin
                );


        View googleButton =
                findViewById(
                        R.id.buttonGoogleLogin
                );


        View registerButton =
                findViewById(
                        R.id.buttonRegisterAdmin
                );


        // =========================================================
        // LOGIN CORREO
        // =========================================================

        loginButton.setOnClickListener(
                view -> signIn()
        );


        // =========================================================
        // LOGIN GOOGLE
        // =========================================================

        googleButton.setOnClickListener(
                view ->
                        startGoogleFlow(
                                false,
                                "",
                                ""
                        )
        );


        // =========================================================
        // REGISTRO
        // =========================================================

        registerButton.setOnClickListener(
                view ->
                        showRegisterOptions()
        );
    }


    // =============================================================
    // PANTALLA PÚBLICA
    // =============================================================

    @Override
    protected boolean isPublicScreen() {

        /*
         * Esta pantalla debe poder abrirse sin
         * estar autenticado.
         */
        return true;
    }


    // =============================================================
    // CONTEXTO
    // =============================================================

    @Override
    protected boolean isAdminContext() {

        /*
         * IMPORTANTE:
         *
         * Estar viendo el LOGIN administrativo
         * todavía NO significa estar en modo ADMIN.
         *
         * El modo ADMIN solamente se activa después
         * de una autenticación correcta.
         */
        return false;
    }


    // =============================================================
    // CONFIGURAR GOOGLE
    // =============================================================

    private void setupGoogleSignIn() {

        googleSignInLauncher =
                registerForActivityResult(
                        new ActivityResultContracts
                                .StartActivityForResult(),

                        result ->
                                handleGoogleResult(
                                        result.getData()
                                )
                );


        if (BuildConfig.GOOGLE_WEB_CLIENT_ID
                .trim()
                .isEmpty()) {

            return;
        }


        GoogleSignInOptions options =
                new GoogleSignInOptions.Builder(
                        GoogleSignInOptions.DEFAULT_SIGN_IN
                )
                        .requestIdToken(
                                BuildConfig.GOOGLE_WEB_CLIENT_ID
                        )
                        .requestEmail()
                        .build();


        googleSignInClient =
                GoogleSignIn.getClient(
                        this,
                        options
                );
    }


    // =============================================================
    // LOGIN CORREO + CONTRASEÑA
    // =============================================================

    private void signIn() {

        String email =
                emailInput
                        .getText()
                        .toString()
                        .trim();


        String password =
                passwordInput
                        .getText()
                        .toString();


        // =========================================================
        // VALIDACIONES
        // =========================================================

        if (email.isEmpty()
                || password.isEmpty()) {

            showMessage(
                    "Escribe correo y contraseña"
            );

            return;
        }


        if (!ValidationUtils
                .isValidEmail(email)) {

            showMessage(
                    "Escribe un correo válido"
            );

            return;
        }


        if (!ValidationUtils
                .hasReasonableTextLength(
                        password,
                        ValidationUtils.MAX_SHORT_TEXT_LENGTH
                )) {

            showMessage(
                    "La contraseña es demasiado larga"
            );

            return;
        }


        // =========================================================
        // AUTENTICAR
        // =========================================================

        store.signIn(
                email,
                password,
                new StoreCallback<Void>() {

                    @Override
                    public void onSuccess(
                            Void result) {

                        /*
                         * Solo AHORA podemos activar
                         * oficialmente el modo ADMIN.
                         */
                        completeAdminAccess(
                                "Sesión iniciada"
                        );
                    }


                    @Override
                    public void onError(
                            String message) {

                        /*
                         * Si falla el login no cambiamos
                         * AppMode.
                         *
                         * El usuario continúa siendo CLIENT.
                         */
                        showMessage(message);
                    }
                }
        );
    }


    // =============================================================
    // OPCIONES DE REGISTRO
    // =============================================================

    private void showRegisterOptions() {

        new AlertDialog.Builder(this)
                .setTitle(
                        "Registrar taller"
                )
                .setItems(
                        new String[]{
                                "Con correo y contraseña",
                                "Con Google"
                        },

                        (dialog, which) -> {

                            if (which == 0) {

                                showEmailRegisterDialog();

                            } else {

                                showGoogleRegisterDialog();
                            }
                        }
                )
                .show();
    }


    // =============================================================
    // REGISTRO CON CORREO
    // =============================================================

    private void showEmailRegisterDialog() {

        LinearLayout form =
                buildDialogForm();


        EditText email =
                addDialogInput(
                        form,
                        "Correo",
                        InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
                );


        EditText password =
                addDialogInput(
                        form,
                        "Contraseña",
                        InputType.TYPE_CLASS_TEXT
                                | InputType.TYPE_TEXT_VARIATION_PASSWORD
                );


        EditText workshop =
                addDialogInput(
                        form,
                        "Nombre del taller",
                        InputType.TYPE_CLASS_TEXT
                );


        Spinner country =
                addCountrySpinner(form);


        EditText whatsapp =
                addDialogInput(
                        form,
                        "WhatsApp del taller",
                        InputType.TYPE_CLASS_PHONE
                );


        AlertDialog dialog =
                new AlertDialog.Builder(this)
                        .setTitle(
                                "Registro con correo"
                        )
                        .setView(form)
                        .setNegativeButton(
                                "Cancelar",
                                null
                        )
                        .setPositiveButton(
                                "Registrar",
                                null
                        )
                        .create();


        dialog.setOnShowListener(
                shownDialog ->

                        dialog.getButton(
                                        AlertDialog.BUTTON_POSITIVE
                                )
                                .setOnClickListener(view -> {

                                    if (registerWithEmail(
                                            email,
                                            password,
                                            workshop,
                                            country,
                                            whatsapp
                                    )) {

                                        dialog.dismiss();
                                    }
                                })
        );


        dialog.show();
    }


    // =============================================================
    // EJECUTAR REGISTRO POR CORREO
    // =============================================================

    private boolean registerWithEmail(
            EditText email,
            EditText password,
            EditText workshop,
            Spinner country,
            EditText whatsapp) {


        String emailValue =
                email
                        .getText()
                        .toString()
                        .trim();


        String passwordValue =
                password
                        .getText()
                        .toString();


        String workshopValue =
                workshop
                        .getText()
                        .toString()
                        .trim();


        String whatsappValue =
                buildWhatsapp(
                        country,
                        whatsapp
                );


        // =========================================================
        // VALIDACIONES
        // =========================================================

        if (emailValue.isEmpty()
                || passwordValue.isEmpty()) {

            showMessage(
                    "Correo y contraseña son obligatorios"
            );

            return false;
        }


        if (!ValidationUtils
                .isValidEmail(emailValue)) {

            showMessage(
                    "Escribe un correo válido"
            );

            return false;
        }


        if (passwordValue.length() < 6
                || passwordValue.length()
                > ValidationUtils.MAX_SHORT_TEXT_LENGTH) {

            showMessage(
                    "La contraseña debe tener entre 6 y 80 caracteres"
            );

            return false;
        }


        if (workshopValue.isEmpty()) {

            showMessage(
                    "El nombre del taller es obligatorio"
            );

            return false;
        }


        if (!ValidationUtils
                .hasReasonableTextLength(
                        workshopValue,
                        ValidationUtils.MAX_SHORT_TEXT_LENGTH
                )) {

            showMessage(
                    "El nombre del taller es demasiado largo"
            );

            return false;
        }


        if (!ValidationUtils
                .isValidPhone(
                        whatsappValue
                )) {

            showMessage(
                    "Escribe un WhatsApp válido"
            );

            return false;
        }


        // =========================================================
        // REGISTRO SUPABASE
        // =========================================================

        store.signUpAdmin(
                emailValue,
                passwordValue,
                workshopValue,
                whatsappValue,

                new StoreCallback<Void>() {

                    @Override
                    public void onSuccess(
                            Void result) {

                        /*
                         * El registro terminó correctamente
                         * y Supabase ya dejó la sesión preparada.
                         */
                        completeAdminAccess(
                                "Administrador registrado"
                        );
                    }


                    @Override
                    public void onError(
                            String message) {

                        /*
                         * IMPORTANTE:
                         *
                         * En algunos casos Supabase puede
                         * requerir confirmar el correo.
                         *
                         * En ese caso NO activamos ADMIN.
                         */
                        showMessage(message);
                    }
                }
        );


        return true;
    }


    // =============================================================
    // REGISTRO CON GOOGLE
    // =============================================================

    private void showGoogleRegisterDialog() {

        LinearLayout form =
                buildDialogForm();


        EditText workshop =
                addDialogInput(
                        form,
                        "Nombre del taller",
                        InputType.TYPE_CLASS_TEXT
                );


        Spinner country =
                addCountrySpinner(form);


        EditText whatsapp =
                addDialogInput(
                        form,
                        "WhatsApp del taller",
                        InputType.TYPE_CLASS_PHONE
                );


        AlertDialog dialog =
                new AlertDialog.Builder(this)
                        .setTitle(
                                "Registro con Google"
                        )
                        .setView(form)
                        .setNegativeButton(
                                "Cancelar",
                                null
                        )
                        .setPositiveButton(
                                "Continuar",
                                null
                        )
                        .create();


        dialog.setOnShowListener(
                shownDialog ->

                        dialog.getButton(
                                        AlertDialog.BUTTON_POSITIVE
                                )
                                .setOnClickListener(view -> {

                                    if (startGoogleRegisterFlow(
                                            workshop
                                                    .getText()
                                                    .toString(),

                                            buildWhatsapp(
                                                    country,
                                                    whatsapp
                                            )
                                    )) {

                                        dialog.dismiss();
                                    }
                                })
        );


        dialog.show();
    }


    // =============================================================
    // VALIDAR REGISTRO GOOGLE
    // =============================================================

    private boolean startGoogleRegisterFlow(
            String workshopName,
            String whatsapp) {


        String workshopValue =
                workshopName.trim();


        if (workshopValue.isEmpty()) {

            showMessage(
                    "El nombre del taller es obligatorio"
            );

            return false;
        }


        if (!ValidationUtils
                .hasReasonableTextLength(
                        workshopValue,
                        ValidationUtils.MAX_SHORT_TEXT_LENGTH
                )) {

            showMessage(
                    "El nombre del taller es demasiado largo"
            );

            return false;
        }


        if (!ValidationUtils
                .isValidPhone(whatsapp)) {

            showMessage(
                    "Escribe un WhatsApp válido"
            );

            return false;
        }


        startGoogleFlow(
                true,
                workshopValue,
                whatsapp
        );


        return true;
    }


    // =============================================================
    // CREAR FORMULARIO DEL DIÁLOGO
    // =============================================================

    private LinearLayout buildDialogForm() {

        LinearLayout form =
                new LinearLayout(this);


        int padding =
                getResources()
                        .getDimensionPixelSize(
                                R.dimen.login_dialog_padding
                        );


        form.setOrientation(
                LinearLayout.VERTICAL
        );


        form.setPadding(
                padding,
                padding / 2,
                padding,
                0
        );


        return form;
    }


    // =============================================================
    // INPUT DE DIÁLOGO
    // =============================================================

    private EditText addDialogInput(
            LinearLayout form,
            String hint,
            int inputType) {


        EditText input =
                new EditText(this);


        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );


        params.setMargins(
                0,
                10,
                0,
                0
        );


        input.setLayoutParams(params);

        input.setHint(hint);

        input.setInputType(inputType);

        input.setSingleLine(true);

        form.addView(input);


        return input;
    }


    // =============================================================
    // SELECTOR DE PAÍS
    // =============================================================

    private Spinner addCountrySpinner(
            LinearLayout form) {


        TextView label =
                new TextView(this);


        label.setText(
                "País del WhatsApp"
        );


        label.setTextColor(
                getColor(
                        R.color.clarys_muted
                )
        );


        LinearLayout.LayoutParams labelParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );


        labelParams.setMargins(
                0,
                14,
                0,
                0
        );


        label.setLayoutParams(
                labelParams
        );


        form.addView(label);


        // =========================================================
        // SPINNER
        // =========================================================

        Spinner spinner =
                new Spinner(this);


        ArrayAdapter<CountryOption> adapter =
                new ArrayAdapter<>(
                        this,
                        android.R.layout.simple_spinner_item,
                        countryOptions
                );


        adapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item
        );


        spinner.setAdapter(adapter);


        spinner.setSelection(
                defaultCountryIndex()
        );


        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );


        params.setMargins(
                0,
                6,
                0,
                0
        );


        spinner.setLayoutParams(params);


        form.addView(spinner);


        return spinner;
    }


    // =============================================================
    // PAÍS POR DEFECTO
    // =============================================================

    private int defaultCountryIndex() {

        String countryCode =
                Locale
                        .getDefault()
                        .getCountry();


        if (countryCode == null
                || countryCode.trim().isEmpty()) {

            countryCode = "CO";
        }


        for (int i = 0;
             i < countryOptions.length;
             i++) {

            if (countryOptions[i]
                    .isoCode
                    .equalsIgnoreCase(
                            countryCode
                    )) {

                return i;
            }
        }


        return 0;
    }


    // =============================================================
    // CREAR WHATSAPP
    // =============================================================

    private String buildWhatsapp(
            Spinner country,
            EditText whatsapp) {


        CountryOption option =
                (CountryOption)
                        country.getSelectedItem();


        String rawPhone =
                ValidationUtils.digitsOnly(
                        whatsapp
                                .getText()
                                .toString()
                );


        if (rawPhone.isEmpty()) {

            return "";
        }


        return option.dialCode
                + rawPhone;
    }


    // =============================================================
    // INICIAR GOOGLE
    // =============================================================

    private void startGoogleFlow(
            boolean register,
            String workshopName,
            String whatsapp) {


        if (googleSignInClient == null) {

            showMessage(
                    "Google requiere GOOGLE_WEB_CLIENT_ID en local.properties"
            );

            return;
        }


        registeringWithGoogle =
                register;


        pendingWorkshopName =
                workshopName;


        pendingWorkshopWhatsapp =
                whatsapp;


        /*
         * Cerramos una posible cuenta anterior para
         * permitir escoger correctamente la cuenta
         * administrativa.
         */
        googleSignInClient
                .signOut()
                .addOnCompleteListener(
                        task ->
                                googleSignInLauncher.launch(
                                        googleSignInClient
                                                .getSignInIntent()
                                )
                );
    }


    // =============================================================
    // RESULTADO GOOGLE
    // =============================================================

    private void handleGoogleResult(
            Intent data) {


        Task<GoogleSignInAccount> task =
                GoogleSignIn
                        .getSignedInAccountFromIntent(
                                data
                        );


        try {

            GoogleSignInAccount account =
                    task.getResult(
                            ApiException.class
                    );


            String idToken =
                    account == null
                            ? null
                            : account.getIdToken();


            if (idToken == null
                    || idToken.trim().isEmpty()) {

                showMessage(
                        "Google no entregó el token de acceso"
                );

                return;
            }


            if (registeringWithGoogle) {

                registerWithGoogle(
                        idToken
                );

            } else {

                loginWithGoogle(
                        idToken
                );
            }


        } catch (ApiException exception) {

            showMessage(
                    "No se pudo iniciar sesión con Google"
            );
        }
    }


    // =============================================================
    // LOGIN GOOGLE
    // =============================================================

    private void loginWithGoogle(
            String idToken) {


        store.signInWithGoogle(
                idToken,
                new StoreCallback<Void>() {

                    @Override
                    public void onSuccess(
                            Void result) {

                        completeAdminAccess(
                                "Sesión iniciada con Google"
                        );
                    }


                    @Override
                    public void onError(
                            String message) {

                        showMessage(message);
                    }
                }
        );
    }


    // =============================================================
    // REGISTRO GOOGLE
    // =============================================================

    private void registerWithGoogle(
            String idToken) {


        store.signUpAdminWithGoogle(
                idToken,
                pendingWorkshopName,
                pendingWorkshopWhatsapp,

                new StoreCallback<Void>() {

                    @Override
                    public void onSuccess(
                            Void result) {

                        completeAdminAccess(
                                "Taller registrado con Google"
                        );
                    }


                    @Override
                    public void onError(
                            String message) {

                        showMessage(message);
                    }
                }
        );
    }


    // =============================================================
    // AUTENTICACIÓN ADMIN COMPLETADA
    // =============================================================

    private void completeAdminAccess(
            String successMessage) {


        /*
         * Este es el ÚNICO punto de esta Activity donde
         * activamos oficialmente el modo ADMIN.
         *
         * Llegamos aquí solamente después de que Supabase
         * confirmó correctamente el login o registro.
         */
        AppModeManager.enterAdminMode(this);


        if (successMessage != null
                && !successMessage.trim().isEmpty()) {

            showMessage(
                    successMessage
            );
        }


        // =========================================================
        // ABRIR DASHBOARD
        // =========================================================

        Intent intent =
                new Intent(
                        this,
                        MainActivity.class
                );


        /*
         * Limpiamos completamente el historial.
         *
         * Así Back desde MainActivity no puede regresar a:
         *
         * AdminAccessActivity
         * CatalogActivity
         * LoginActivity
         */
        intent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TASK
        );


        startActivity(intent);

        finish();
    }


    // =============================================================
    // MODELO PAÍS
    // =============================================================

    private static class CountryOption {

        private final String isoCode;

        private final String name;

        private final String dialCode;


        CountryOption(
                String isoCode,
                String name,
                String dialCode) {

            this.isoCode =
                    isoCode;

            this.name =
                    name;

            this.dialCode =
                    dialCode;
        }


        @Override
        public String toString() {

            return name
                    + " ("
                    + dialCode
                    + ")";
        }
    }
}