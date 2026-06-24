package com.clarys.app;

import android.os.Bundle;
import android.widget.EditText;

import com.clarys.app.data.MockStore;

public class SettingsActivity extends BaseScreenActivity {
    private final MockStore store = MockStore.getInstance();
    private EditText businessInput;
    private EditText whatsappInput;
    private EditText currencyInput;
    private EditText minStockInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

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
        store.setBusinessName(businessInput.getText().toString());
        store.setContactWhatsapp(whatsappInput.getText().toString());
        store.setCurrency(currencyInput.getText().toString());
        try {
            store.setDefaultMinStock(Integer.parseInt(minStockInput.getText().toString().trim()));
        } catch (NumberFormatException exception) {
            store.setDefaultMinStock(5);
        }
        showMessage("Configuracion guardada en memoria");
        openScreen(MainActivity.class);
        finish();
    }
}
