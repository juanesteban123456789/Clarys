package com.clarys.app;

import android.content.Intent;
import android.os.Bundle;
import android.widget.EditText;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.clarys.app.data.MockStore;
import com.clarys.app.model.Product;
import com.clarys.app.ui.ProductAdapter;

public class CatalogActivity extends BaseScreenActivity {
    private final MockStore store = MockStore.getInstance();
    private ProductAdapter adapter;
    private EditText searchInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_catalog);

        setupHeader(R.id.buttonHeaderHome, R.id.buttonHeaderBack);
        bindNavigation(R.id.buttonBottomHome, MainActivity.class);
        bindNavigation(R.id.buttonBottomCategories, CatalogActivity.class);
        bindNavigation(R.id.buttonBottomCart, CartActivity.class);
        bindNavigation(R.id.buttonBottomProfile, ProductListActivity.class);
        bindNavigation(R.id.buttonGoToSale, SaleActivity.class);
        bindMessage(R.id.buttonSearchVisual, "Busqueda visual disponible, logica pendiente");

        searchInput = findViewById(R.id.inputCatalogSearch);
        RecyclerView productsList = findViewById(R.id.recyclerCatalogProducts);
        productsList.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ProductAdapter("Ver detalle", "Agregar", false, new ProductAdapter.ProductActionListener() {
            @Override
            public void onPrimaryAction(Product product) {
                openProductDetail(product.getId());
            }

            @Override
            public void onSecondaryAction(Product product) {
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
    protected void onResume() {
        super.onResume();
        refreshProducts();
    }

    private void refreshProducts() {
        adapter.submitList(store.searchProducts(searchInput.getText().toString(), "Todos"));
    }

    private void openProductDetail(int productId) {
        Intent intent = new Intent(this, ProductDetailActivity.class);
        intent.putExtra("productId", productId);
        startActivity(intent);
    }
}
