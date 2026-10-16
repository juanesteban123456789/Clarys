package com.clarys.app;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.clarys.app.data.StoreCallback;
import com.clarys.app.data.SupabaseStore;
import com.clarys.app.model.Product;
import com.clarys.app.ui.ProductAdapter;

import java.util.List;

public class ProductListActivity extends BaseScreenActivity {
    public static final String EXTRA_PRODUCT_FILTER = "productFilter";

    private static final String[] PRODUCT_FILTERS = {
            "Todos", "Stock bajo", "Agotados", "Inactivos"
    };

    private SupabaseStore store;
    private ProductAdapter adapter;
    private EditText searchInput;
    private Spinner filterSpinner;
    private RecyclerView productsList;
    private ProgressBar productsProgress;
    private LinearLayout productsState;
    private TextView productsStateTitle;
    private TextView productsStateMessage;
    private Button productsStateAction;

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
                PRODUCT_FILTERS));
        applyRequestedFilter();

        productsList = findViewById(R.id.recyclerAdminProducts);
        productsProgress = findViewById(R.id.progressAdminProducts);
        productsState = findViewById(R.id.layoutAdminProductsState);
        productsStateTitle = findViewById(R.id.textAdminProductsStateTitle);
        productsStateMessage = findViewById(R.id.textAdminProductsStateMessage);
        productsStateAction = findViewById(R.id.buttonAdminProductsStateAction);
        productsList.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ProductAdapter("Editar", "Detalle", true, new ProductAdapter.ProductActionListener() {
            @Override
            public void onPrimaryAction(Product product) {
                Intent intent = new Intent(ProductListActivity.this, ProductFormActivity.class);
                intent.putExtra("productId", product.getId());
                intent.putExtra(CatalogActivity.EXTRA_ADMIN_CATALOG, true);
                startActivity(intent);
            }

            @Override
            public void onSecondaryAction(Product product) {
                Intent intent = new Intent(ProductListActivity.this, ProductDetailActivity.class);
                intent.putExtra("productId", product.getId());
                intent.putExtra(CatalogActivity.EXTRA_ADMIN_CATALOG, true);
                startActivity(intent);
            }
        });
        productsList.setAdapter(adapter);
        findViewById(R.id.buttonSearchProducts).setOnClickListener(view -> refreshProducts());
    }

    private void applyRequestedFilter() {
        String requestedFilter = getIntent().getStringExtra(EXTRA_PRODUCT_FILTER);
        if (requestedFilter == null) {
            return;
        }

        for (int position = 0; position < PRODUCT_FILTERS.length; position++) {
            if (PRODUCT_FILTERS[position].equalsIgnoreCase(requestedFilter)) {
                filterSpinner.setSelection(position);
                return;
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadProducts();
    }

    private void loadProducts() {
        showProductsLoading();
        store.refreshProducts(true, new StoreCallback<java.util.List<Product>>() {
            @Override
            public void onSuccess(java.util.List<Product> result) {
                refreshProducts();
            }

            @Override
            public void onError(String message) {
                if (store.getProducts().isEmpty()) {
                    showProductsState(
                            "No pudimos cargar los productos",
                            "Revisa tu conexión e inténtalo nuevamente.",
                            "Reintentar",
                            view -> loadProducts());
                } else {
                    showMessage(message);
                    refreshProducts();
                }
            }
        });
    }

    private void refreshProducts() {
        List<Product> products = store.searchProducts(searchInput.getText().toString(),
                filterSpinner.getSelectedItem().toString());
        adapter.submitList(products);

        if (!products.isEmpty()) {
            showProductsContent();
            return;
        }

        if (store.getProducts().isEmpty()) {
            showProductsState(
                    "Aún no tienes productos",
                    "Crea tu primer producto para comenzar a gestionar el inventario.",
                    "Crear producto",
                    view -> startActivity(new Intent(this, ProductFormActivity.class)));
        } else {
            showProductsState(
                    "Sin coincidencias",
                    "Prueba con otro texto o cambia el filtro seleccionado.",
                    "Limpiar filtros",
                    view -> {
                        searchInput.setText("");
                        filterSpinner.setSelection(0);
                        refreshProducts();
                    });
        }
    }

    private void showProductsLoading() {
        productsProgress.setVisibility(View.VISIBLE);
        productsState.setVisibility(View.GONE);
        productsList.setVisibility(View.GONE);
    }

    private void showProductsContent() {
        productsProgress.setVisibility(View.GONE);
        productsState.setVisibility(View.GONE);
        productsList.setVisibility(View.VISIBLE);
    }

    private void showProductsState(String title, String message, String actionLabel,
                                   View.OnClickListener action) {
        productsProgress.setVisibility(View.GONE);
        productsList.setVisibility(View.GONE);
        productsState.setVisibility(View.VISIBLE);
        productsStateTitle.setText(title);
        productsStateMessage.setText(message);
        productsStateAction.setText(actionLabel);
        productsStateAction.setOnClickListener(action);
    }
}
