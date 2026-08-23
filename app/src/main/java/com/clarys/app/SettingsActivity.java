package com.clarys.app;

import android.os.Bundle;
import android.widget.EditText;

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
