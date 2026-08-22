package com.clarys.app;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.clarys.app.data.StoreCallback;
import com.clarys.app.data.SupabaseStore;
import com.clarys.app.model.Product;
import com.clarys.app.ui.ProductAdapter;

public class CatalogActivity extends BaseScreenActivity {
    private SupabaseStore store;
    private ProductAdapter adapter;
    private EditText searchInput;
    private boolean adminCatalog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_catalog);
        store = SupabaseStore.getInstance(this);
        adminCatalog = getIntent().getBooleanExtra("adminCatalog", false);
        if (adminCatalog && !store.isAuthenticated()) {
            openScreen(LoginActivity.class);
            finish();
            return;
        }

        setupHeader(R.id.buttonHeaderHome, R.id.buttonHeaderBack);
        bindNavigation(R.id.buttonBottomHome, CatalogActivity.class);
        bindNavigation(R.id.buttonBottomCategories, CatalogActivity.class);
        if (adminCatalog) {
            findViewById(R.id.buttonBottomCart).setOnClickListener(view -> openScreen(ProductListActivity.class));
        } else {
            bindNavigation(R.id.buttonBottomCart, CartActivity.class);
        }
        findViewById(R.id.buttonBottomProfile).setOnClickListener(view -> {
            if (store.isAuthenticated()) {
                openScreen(ProductListActivity.class);
            } else {
                openScreen(LoginActivity.class);
            }
        });
        findViewById(R.id.buttonGoToSale).setOnClickListener(view -> {
            if (store.isAuthenticated()) {
                openScreen(SaleActivity.class);
            } else {
                openScreen(LoginActivity.class);
            }
        });
        bindMessage(R.id.buttonSearchVisual, "Búsqueda visual disponible, lógica pendiente");

        searchInput = findViewById(R.id.inputCatalogSearch);
        RecyclerView productsList = findViewById(R.id.recyclerCatalogProducts);
        productsList.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ProductAdapter("Ver detalle", adminCatalog ? "Editar" : "Agregar", adminCatalog,
                new ProductAdapter.ProductActionListener() {
            @Override
            public void onPrimaryAction(Product product) {
                openProductDetail(product.getId());
            }

            @Override
            public void onSecondaryAction(Product product) {
                if (adminCatalog) {
                    Intent intent = new Intent(CatalogActivity.this, ProductFormActivity.class);
                    intent.putExtra("productId", product.getId());
                    startActivity(intent);
                    return;
                }
                if (store.addToCart(product.getId(), 1)) {
                    showMessage("Producto agregado al carrito");
                } else {
                    showMessage("Producto sin stock disponible");
                }
            }
        });
        productsList.setAdapter(adapter);
        findViewById(R.id.buttonCatalogSearch).setOnClickListener(view -> refreshProducts());
    }

    @Override
    protected boolean isPublicScreen() {
        return true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        renderAccessControls();
        store.refreshProducts(adminCatalog, new StoreCallback<java.util.List<Product>>() {
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

    private void renderAccessControls() {
        Button saleButton = findViewById(R.id.buttonGoToSale);
        TextView adminButton = findViewById(R.id.buttonBottomProfile);
        TextView cartButton = findViewById(R.id.buttonBottomCart);
        boolean authenticated = store.isAuthenticated();
        saleButton.setVisibility(View.GONE);
        adminButton.setText(authenticated ? "Admin" : "Acceso");
        cartButton.setText(adminCatalog ? "Productos" : "Carrito");
    }

    private void refreshProducts() {
        adapter.submitList(store.searchProducts(searchInput.getText().toString(), "Todos"));
    }

    private void openProductDetail(int productId) {
        Intent intent = new Intent(this, ProductDetailActivity.class);
        intent.putExtra("productId", productId);
        intent.putExtra("adminCatalog", adminCatalog);
        startActivity(intent);
    }
}
