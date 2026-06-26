package com.clarys.app;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.clarys.app.data.StoreCallback;
import com.clarys.app.data.SupabaseStore;
import com.clarys.app.model.Product;
import com.clarys.app.ui.ProductAdapter;

public class ProductListActivity extends BaseScreenActivity {
    private SupabaseStore store;
    private ProductAdapter adapter;
    private EditText searchInput;
    private Spinner filterSpinner;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_product_list);
        store = SupabaseStore.getInstance(this);

        setupHeader(R.id.buttonHeaderHome, R.id.buttonHeaderBack);
        bindNavigation(R.id.buttonAddProduct, ProductFormActivity.class);
        bindNavigation(R.id.buttonOpenInventoryFromProducts, InventoryActivity.class);

        searchInput = findViewById(R.id.inputProductSearch);
        filterSpinner = findViewById(R.id.spinnerProductFilter);
        filterSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
                new String[]{"Todos", "Stock bajo", "Agotados", "Inactivos"}));

        RecyclerView productsList = findViewById(R.id.recyclerAdminProducts);
        productsList.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ProductAdapter("Editar", "Detalle", true, new ProductAdapter.ProductActionListener() {
            @Override
            public void onPrimaryAction(Product product) {
                Intent intent = new Intent(ProductListActivity.this, ProductFormActivity.class);
                intent.putExtra("productId", product.getId());
                startActivity(intent);
            }

            @Override
            public void onSecondaryAction(Product product) {
                Intent intent = new Intent(ProductListActivity.this, ProductDetailActivity.class);
                intent.putExtra("productId", product.getId());
                startActivity(intent);
            }
        });
        productsList.setAdapter(adapter);
        findViewById(R.id.buttonSearchProducts).setOnClickListener(view -> refreshProducts());
    }

    @Override
    protected void onResume() {
        super.onResume();
        store.refreshProducts(true, new StoreCallback<java.util.List<Product>>() {
            @Override
            public void onSuccess(java.util.List<Product> result) {
                refreshProducts();
            }

            @Override
            public void onError(String message) {
                showMessage(message);
                refreshProducts();
            }
        });
    }

    private void refreshProducts() {
        adapter.submitList(store.searchProducts(searchInput.getText().toString(),
                filterSpinner.getSelectedItem().toString()));
    }
}
