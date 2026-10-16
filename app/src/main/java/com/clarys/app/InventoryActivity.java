package com.clarys.app;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.clarys.app.data.StoreCallback;
import com.clarys.app.data.SupabaseStore;
import com.clarys.app.model.Product;
import com.clarys.app.ui.CatalogProductAdapter;
import com.clarys.app.ui.InventoryMovementAdapter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class InventoryActivity extends BaseScreenActivity {
    private SupabaseStore store;
    private CatalogProductAdapter productAdapter;
    private InventoryMovementAdapter movementAdapter;
    private Spinner categorySpinner;
    private final List<Product> inventoryProducts = new ArrayList<>();
    private RecyclerView productsList;
    private ProgressBar productsProgress;
    private LinearLayout productsState;
    private TextView productsStateTitle;
    private TextView productsStateMessage;
    private Button productsStateAction;
    private RecyclerView movementList;
    private ProgressBar movementsProgress;
    private LinearLayout movementsState;
    private TextView movementsStateTitle;
    private TextView movementsStateMessage;
    private Button movementsStateAction;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_inventory);
        store = SupabaseStore.getInstance(this);

        setupHeader(R.id.buttonHeaderHome, R.id.buttonHeaderBack);
        bindNavigation(R.id.buttonGoToManagement, ProductListActivity.class);
        bindNavigation(R.id.buttonNewMovement, InventoryMovementActivity.class);

        categorySpinner = findViewById(R.id.spinnerInventoryCategory);
        categorySpinner.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item,
                new String[]{"Todos"}));
        categorySpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                applyCategoryFilter();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                applyCategoryFilter();
            }
        });

        productsList = findViewById(R.id.recyclerInventoryProducts);
        productsProgress = findViewById(R.id.progressInventoryProducts);
        productsState = findViewById(R.id.layoutInventoryProductsState);
        productsStateTitle = findViewById(R.id.textInventoryProductsStateTitle);
        productsStateMessage = findViewById(R.id.textInventoryProductsStateMessage);
        productsStateAction = findViewById(R.id.buttonInventoryProductsStateAction);
        productsList.setLayoutManager(new LinearLayoutManager(
                this,
                LinearLayoutManager.HORIZONTAL,
                false));
        productAdapter = new CatalogProductAdapter(true, false, "Ajustar",
                new CatalogProductAdapter.ProductActionListener() {
            @Override
            public void onOpenProduct(Product product) {
                Intent intent = new Intent(InventoryActivity.this, InventoryMovementActivity.class);
                intent.putExtra("productId", product.getId());
                startActivity(intent);
            }

            @Override
            public void onSecondaryAction(Product product) {
                Intent intent = new Intent(InventoryActivity.this, ProductFormActivity.class);
                intent.putExtra("productId", product.getId());
                startActivity(intent);
            }
        });
        productsList.setAdapter(productAdapter);

        movementList = findViewById(R.id.recyclerInventoryMovements);
        movementsProgress = findViewById(R.id.progressInventoryMovements);
        movementsState = findViewById(R.id.layoutInventoryMovementsState);
        movementsStateTitle = findViewById(R.id.textInventoryMovementsStateTitle);
        movementsStateMessage = findViewById(R.id.textInventoryMovementsStateMessage);
        movementsStateAction = findViewById(R.id.buttonInventoryMovementsStateAction);
        movementList.setLayoutManager(new LinearLayoutManager(this));
        movementAdapter = new InventoryMovementAdapter();
        movementList.setAdapter(movementAdapter);
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadProducts();
        loadMovements();
    }

    private void loadProducts() {
        showProductsLoading();
        store.refreshProducts(true, new StoreCallback<java.util.List<Product>>() {
            @Override
            public void onSuccess(java.util.List<Product> result) {
                updateInventoryProducts(result);
                renderSummary();
            }

            @Override
            public void onError(String message) {
                List<Product> cachedProducts = store.getProducts();
                updateInventoryProducts(cachedProducts);
                renderSummary();
                if (cachedProducts.isEmpty()) {
                    showProductsState(
                            "No pudimos cargar el inventario",
                            "Revisa tu conexión e inténtalo nuevamente.",
                            "Reintentar",
                            view -> loadProducts());
                } else {
                    showMessage(message);
                }
            }
        });
    }

    private void loadMovements() {
        showMovementsLoading();
        store.refreshMovements(new StoreCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                renderMovements();
            }

            @Override
            public void onError(String message) {
                renderMovements();
                if (store.getMovements().isEmpty()) {
                    showMovementsState(
                            "No pudimos cargar los movimientos",
                            "Revisa tu conexión e inténtalo nuevamente.",
                            "Reintentar",
                            view -> loadMovements());
                }
            }
        });
    }

    private void renderSummary() {
        ((TextView) findViewById(R.id.textInventoryValue)).setText(store.formatMoney(store.getInventoryValue()));
        ((TextView) findViewById(R.id.textInventoryAlerts)).setText(String.valueOf(store.getLowStockCount()));
    }

    private void updateInventoryProducts(List<Product> products) {
        String selectedCategory = categorySpinner.getSelectedItem() == null
                ? "Todos"
                : categorySpinner.getSelectedItem().toString();

        inventoryProducts.clear();
        if (products != null) {
            inventoryProducts.addAll(products);
        }

        Set<String> uniqueCategories = new LinkedHashSet<>();
        for (Product product : inventoryProducts) {
            String category = product.getCategory() == null ? "" : product.getCategory().trim();
            if (!category.isEmpty()) {
                uniqueCategories.add(category);
            }
        }

        List<String> categories = new ArrayList<>(uniqueCategories);
        categories.sort(Comparator.comparing(String::toLowerCase));
        categories.add(0, "Todos");

        categorySpinner.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item,
                categories));
        int selectedPosition = categories.indexOf(selectedCategory);
        categorySpinner.setSelection(Math.max(0, selectedPosition));
        applyCategoryFilter();
    }

    private void applyCategoryFilter() {
        if (categorySpinner == null || productAdapter == null) {
            return;
        }

        String selectedCategory = categorySpinner.getSelectedItem() == null
                ? "Todos"
                : categorySpinner.getSelectedItem().toString();
        List<Product> filteredProducts = new ArrayList<>();
        for (Product product : inventoryProducts) {
            if ("Todos".equalsIgnoreCase(selectedCategory)
                    || selectedCategory.equalsIgnoreCase(product.getCategory())) {
                filteredProducts.add(product);
            }
        }
        productAdapter.submitList(filteredProducts);

        if (!filteredProducts.isEmpty()) {
            showProductsContent();
        } else if (inventoryProducts.isEmpty()) {
            showProductsState(
                    "Inventario vacío",
                    "Agrega un producto para comenzar a controlar sus existencias.",
                    "Crear producto",
                    view -> startActivity(new Intent(this, ProductFormActivity.class)));
        } else {
            showProductsState(
                    "Sin productos en esta categoría",
                    "Selecciona otra categoría para consultar sus existencias.",
                    "Ver todos",
                    view -> categorySpinner.setSelection(0));
        }
    }

    private void renderMovements() {
        movementAdapter.submitList(store.getMovements());
        if (store.getMovements().isEmpty()) {
            showMovementsState(
                    "Sin movimientos registrados",
                    "Las entradas y salidas de inventario aparecerán aquí.",
                    "Registrar movimiento",
                    view -> startActivity(new Intent(this, InventoryMovementActivity.class)));
        } else {
            showMovementsContent();
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

    private void showMovementsLoading() {
        movementsProgress.setVisibility(View.VISIBLE);
        movementsState.setVisibility(View.GONE);
        movementList.setVisibility(View.GONE);
    }

    private void showMovementsContent() {
        movementsProgress.setVisibility(View.GONE);
        movementsState.setVisibility(View.GONE);
        movementList.setVisibility(View.VISIBLE);
    }

    private void showMovementsState(String title, String message, String actionLabel,
                                    View.OnClickListener action) {
        movementsProgress.setVisibility(View.GONE);
        movementList.setVisibility(View.GONE);
        movementsState.setVisibility(View.VISIBLE);
        movementsStateTitle.setText(title);
        movementsStateMessage.setText(message);
        movementsStateAction.setText(actionLabel);
        movementsStateAction.setOnClickListener(action);
    }
}
