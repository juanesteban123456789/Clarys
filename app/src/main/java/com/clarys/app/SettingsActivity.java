package com.clarys.app;

import android.os.Bundle;
import android.widget.EditText;
import android.content.Intent;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SwitchCompat;
import com.clarys.app.data.OrderNotifications;

import com.clarys.app.data.StoreCallback;
import com.clarys.app.data.SupabaseStore;

public class SettingsActivity extends BaseScreenActivity {
    private SupabaseStore store;
    private EditText businessInput;
    private EditText whatsappInput;
    private EditText currencyInput;
    private EditText minStockInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        store = SupabaseStore.getInstance(this);

        setupHeader(R.id.buttonHeaderHome, R.id.buttonHeaderBack);
        businessInput = findViewById(R.id.inputSettingsBusiness);
        whatsappInput = findViewById(R.id.inputSettingsWhatsapp);
        currencyInput = findViewById(R.id.inputSettingsCurrency);
        minStockInput = findViewById(R.id.inputSettingsMinStock);

        businessInput.setText(store.getBusinessName());
        whatsappInput.setText(store.getContactWhatsapp());
        currencyInput.setText(store.getCurrency());
        minStockInput.setText(String.valueOf(store.getDefaultMinStock()));

        findViewById(R.id.buttonSaveSettings).setOnClickListener(view -> saveSettings());
        SwitchCompat notifications=findViewById(R.id.switchOrderNotifications);
        SwitchCompat dailyReminder = findViewById(R.id.switchDailyOrderReminder);
        dailyReminder.setChecked(OrderNotifications.isDailyReminderEnabled(this));
        dailyReminder.setEnabled(OrderNotifications.isEnabled(this));
        dailyReminder.setOnCheckedChangeListener((button, checked) ->
                OrderNotifications.setDailyReminderEnabled(this, checked));
        findViewById(R.id.buttonOrderNotificationSettings).setOnClickListener(view -> {
            Intent intent = new Intent(android.os.Build.VERSION.SDK_INT >= 26
                    ? android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS
                    : android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                intent.putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, getPackageName());
            } else {
                intent.setData(android.net.Uri.parse("package:" + getPackageName()));
            }
            startActivity(intent);
        });
        notifications.setChecked(OrderNotifications.isEnabled(this));
        notifications.setOnCheckedChangeListener((button,checked)-> {
            OrderNotifications.setEnabled(this,checked);
            dailyReminder.setEnabled(checked);
            if(checked && android.os.Build.VERSION.SDK_INT>=33
                    && androidx.core.content.ContextCompat.checkSelfPermission(this,android.Manifest.permission.POST_NOTIFICATIONS)
                    !=android.content.pm.PackageManager.PERMISSION_GRANTED) {
                showMessage("Activa los avisos de Clarys en los permisos del dispositivo");
                Intent intent=new Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS);
                intent.putExtra(android.provider.Settings.EXTRA_APP_PACKAGE,getPackageName());
                startActivity(intent);
            }
        });
        findViewById(R.id.buttonSettingsHelp).setOnClickListener(view -> openScreen(HelpActivity.class));
        findViewById(R.id.buttonSettingsPrivacy).setOnClickListener(view -> {
            Intent intent=new Intent(this,HelpActivity.class); intent.putExtra("privacy",true); startActivity(intent);
        });
        findViewById(R.id.buttonDeleteAccount).setOnClickListener(view -> confirmDeleteAccount());
    }

    private void confirmDeleteAccount() {
        EditText password=new EditText(this);
        password.setHint("Contraseña (si usas correo)");
        password.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Eliminar cuenta y taller")
                .setMessage("Se eliminarán la cuenta, el taller, sus productos, pedidos, ventas e imágenes. "
                        +"Esta acción es permanente. Exporta tus datos antes de continuar.")
                .setView(password).setNegativeButton("Conservar cuenta",null).setPositiveButton("Eliminar",null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
            store.deleteAccount(password.getText().toString(),new StoreCallback<Void>() {
                @Override public void onSuccess(Void result) {
                    OrderNotifications.cancel(SettingsActivity.this);
                    dialog.dismiss();
                    Intent intent=new Intent(SettingsActivity.this,AdminAccessActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent); finish();
                }
                @Override public void onError(String message) {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                    password.setError(message);
                }
            });
        }));
        dialog.show();
    }

    private void saveSettings() {
        String businessName = businessInput.getText().toString().trim();
        String whatsapp = whatsappInput.getText().toString().trim();
        String currency = currencyInput.getText().toString().trim().toUpperCase();
        if (businessName.isEmpty()) {
            showMessage("El nombre del taller es obligatorio");
            return;
        }
        if (!ValidationUtils.hasReasonableTextLength(businessName, ValidationUtils.MAX_SHORT_TEXT_LENGTH)) {
            showMessage("El nombre del taller es demasiado largo");
            return;
        }
        if (!ValidationUtils.isValidPhone(whatsapp)) {
            showMessage("Escribe un WhatsApp válido");
            return;
        }
        if (!currency.matches("[A-Z]{3}")) {
            showMessage("La moneda debe tener 3 letras, por ejemplo COP");
            return;
        }

        int minStock;
        try {
            minStock = Integer.parseInt(minStockInput.getText().toString().trim());
            if (minStock < 0 || minStock > ValidationUtils.MAX_STOCK_VALUE) {
                showMessage("El stock mínimo está fuera del rango permitido");
                return;
            }
        } catch (NumberFormatException exception) {
            showMessage("Ingresa un stock mínimo válido");
            return;
        }
        store.saveSettingsAsync(businessName, whatsapp, currency, minStock, new StoreCallback<Void>() {
                    @Override
                    public void onSuccess(Void result) {
                        showMessage("Configuración guardada");
                        openScreen(MainActivity.class);
                        finish();
                    }

                    @Override
                    public void onError(String message) {
                        showMessage(message);
                    }
                });
    }
}
