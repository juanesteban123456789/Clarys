package com.clarys.app;

import android.os.Bundle;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;

import com.clarys.app.data.MockStore;
import com.clarys.app.model.Product;

public class ProductFormActivity extends BaseScreenActivity {
    private final MockStore store = MockStore.getInstance();
    private Integer productId;
    private EditText nameInput;
    private EditText descriptionInput;
    private EditText categoryInput;
    private EditText purchasePriceInput;
    private EditText salePriceInput;
    private EditText stockInput;
    private EditText minStockInput;
    private EditText sizesInput;
    private EditText colorsInput;
    private EditText skuInput;
    private CheckBox activeInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_product_form);

        setupHeader(R.id.buttonHeaderHome, R.id.buttonHeaderBack);
        nameInput = findViewById(R.id.inputProductName);
        descriptionInput = findViewById(R.id.inputProductDescription);
        categoryInput = findViewById(R.id.inputProductCategory);
        purchasePriceInput = findViewById(R.id.inputProductPurchasePrice);
        salePriceInput = findViewById(R.id.inputProductSalePrice);
        stockInput = findViewById(R.id.inputProductStock);
        minStockInput = findViewById(R.id.inputProductMinStock);
        sizesInput = findViewById(R.id.inputProductSizes);
        colorsInput = findViewById(R.id.inputProductColors);
        skuInput = findViewById(R.id.inputProductSku);
        activeInput = findViewById(R.id.checkProductActive);

        int extraProductId = getIntent().getIntExtra("productId", -1);
        productId = extraProductId == -1 ? null : extraProductId;
        renderProduct();

        Button imageButton = findViewById(R.id.buttonProductImage);
        imageButton.setOnClickListener(view -> showMessage("Selector de imagen pendiente de almacenamiento"));
        findViewById(R.id.buttonSaveProductForm).setOnClickListener(view -> saveProduct());
        bindNavigation(R.id.buttonCancelProductForm, ProductListActivity.class);
    }

    private void renderProduct() {
        if (productId == null) {
            minStockInput.setText(String.valueOf(store.getDefaultMinStock()));
            activeInput.setChecked(true);
            return;
        }

        Product product = store.getProduct(productId);
        if (product == null) {
            showMessage("Producto no encontrado");
            finish();
            return;
        }

        nameInput.setText(product.getName());
        descriptionInput.setText(product.getDescription());
        categoryInput.setText(product.getCategory());
        purchasePriceInput.setText(String.valueOf(product.getPurchasePrice()));
        salePriceInput.setText(String.valueOf(product.getSalePrice()));
        stockInput.setText(String.valueOf(product.getStock()));
        minStockInput.setText(String.valueOf(product.getMinStock()));
        sizesInput.setText(product.getSizes());
        colorsInput.setText(product.getColors());
        skuInput.setText(product.getSku());
        activeInput.setChecked(product.isActive());
    }

    private void saveProduct() {
        String name = nameInput.getText().toString().trim();
        if (name.isEmpty()) {
            showMessage("El nombre es obligatorio");
            return;
        }

        store.saveProduct(productId, name,
                descriptionInput.getText().toString(),
                categoryInput.getText().toString(),
                parseInt(purchasePriceInput),
                parseInt(salePriceInput),
                parseInt(stockInput),
                parseInt(minStockInput),
                sizesInput.getText().toString(),
                colorsInput.getText().toString(),
                skuInput.getText().toString(),
                activeInput.isChecked());
        showMessage("Producto guardado");
        openScreen(ProductListActivity.class);
        finish();
    }

    private int parseInt(EditText input) {
        try {
            return Integer.parseInt(input.getText().toString().replace("$", "").replace(".", "").trim());
        } catch (NumberFormatException exception) {
            return 0;
        }
    }
}
