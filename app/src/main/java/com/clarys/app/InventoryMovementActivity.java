package com.clarys.app;

import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;

import com.clarys.app.data.StoreCallback;
import com.clarys.app.data.SupabaseStore;
import com.clarys.app.model.Product;

import java.util.ArrayList;
import java.util.List;

public class InventoryMovementActivity extends BaseScreenActivity {
    private SupabaseStore store;
    private final List<Product> products = new ArrayList<>();
    private Spinner productSpinner;
    private Spinner typeSpinner;
    private EditText quantityInput;
    private EditText reasonInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_inventory_movement);
        store = SupabaseStore.getInstance(this);

        setupHeader(R.id.buttonHeaderHome, R.id.buttonHeaderBack);
        productSpinner = findViewById(R.id.spinnerMovementProduct);
        typeSpinner = findViewById(R.id.spinnerMovementType);
        quantityInput = findViewById(R.id.inputMovementQuantity);
        reasonInput = findViewById(R.id.inputMovementReason);

        loadProducts();
        typeSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
                new String[]{"Entrada", "Salida", "Ajuste"}));

        findViewById(R.id.buttonApplyMovement).setOnClickListener(view -> applyMovement());
        bindNavigation(R.id.buttonCancelMovement, InventoryActivity.class);
    }

    private void loadProducts() {
        products.clear();
        products.addAll(store.getProducts());
        List<String> names = new ArrayList<>();
        for (Product product : products) {
            names.add(product.getName() + " | stock " + product.getStock());
        }
        productSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, names));

        int productId = getIntent().getIntExtra("productId", -1);
        for (int i = 0; i < products.size(); i++) {
            if (products.get(i).getId() == productId) {
                productSpinner.setSelection(i);
                return;
            }
        }
    }

    private void applyMovement() {
        if (products.isEmpty()) {
            showMessage("No hay productos para ajustar");
            return;
        }

        int quantity = parseQuantity();
        if (quantity <= 0) {
            showMessage("Ingresa una cantidad valida");
            return;
        }

        Product selected = products.get(productSpinner.getSelectedItemPosition());
        store.adjustStockAsync(selected.getId(), typeSpinner.getSelectedItem().toString(),
                quantity, reasonInput.getText().toString(), new StoreCallback<Void>() {
                    @Override
                    public void onSuccess(Void result) {
                        showMessage("Movimiento registrado");
                        openScreen(InventoryActivity.class);
                        finish();
                    }

                    @Override
                    public void onError(String message) {
                        showMessage(message);
                    }
                });
    }

    private int parseQuantity() {
        try {
            return Integer.parseInt(quantityInput.getText().toString().trim());
        } catch (NumberFormatException exception) {
            return 0;
        }
    }
}
