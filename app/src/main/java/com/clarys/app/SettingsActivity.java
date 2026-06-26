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
        int minStock;
        try {
            minStock = Integer.parseInt(minStockInput.getText().toString().trim());
        } catch (NumberFormatException exception) {
            minStock = 5;
        }
        store.saveSettingsAsync(businessInput.getText().toString(), whatsappInput.getText().toString(),
                currencyInput.getText().toString(), minStock, new StoreCallback<Void>() {
                    @Override
                    public void onSuccess(Void result) {
                        showMessage("Configuracion guardada");
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
