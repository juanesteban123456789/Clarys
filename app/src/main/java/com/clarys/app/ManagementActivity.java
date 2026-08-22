package com.clarys.app;

import android.os.Bundle;
import android.widget.Button;

public class ManagementActivity extends BaseScreenActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_management);

        setupHeader(R.id.buttonHeaderHome, R.id.buttonHeaderBack);
        bindNavigation(R.id.buttonViewInventory, InventoryActivity.class);

        Button uploadImageButton = findViewById(R.id.buttonUploadImage);
        Button saveProductButton = findViewById(R.id.buttonSaveProduct);

        uploadImageButton.setOnClickListener(view -> showMessage("Carga de imagen pendiente de lógica real"));
        saveProductButton.setOnClickListener(view -> openScreen(ProductFormActivity.class));
    }
}
