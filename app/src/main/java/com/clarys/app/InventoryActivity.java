package com.clarys.app;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.clarys.app.data.MockStore;
import com.clarys.app.model.Product;
import com.clarys.app.ui.InventoryMovementAdapter;
import com.clarys.app.ui.ProductAdapter;

public class InventoryActivity extends BaseScreenActivity {
    private final MockStore store = MockStore.getInstance();
    private ProductAdapter productAdapter;
    private InventoryMovementAdapter movementAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_inventory);

        setupHeader(R.id.buttonHeaderHome, R.id.buttonHeaderBack);
        bindNavigation(R.id.buttonGoToManagement, ProductListActivity.class);
        bindNavigation(R.id.buttonNewMovement, InventoryMovementActivity.class);

        RecyclerView productsList = findViewById(R.id.recyclerInventoryProducts);
        productsList.setLayoutManager(new LinearLayoutManager(this));
        productAdapter = new ProductAdapter("Ajustar", "Editar", true, new ProductAdapter.ProductActionListener() {
            @Override
            public void onPrimaryAction(Product product) {
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

        RecyclerView movementList = findViewById(R.id.recyclerInventoryMovements);
        movementList.setLayoutManager(new LinearLayoutManager(this));
        movementAdapter = new InventoryMovementAdapter();
        movementList.setAdapter(movementAdapter);
    }

    @Override
    protected void onResume() {
        super.onResume();
        productAdapter.submitList(store.getProducts());
        movementAdapter.submitList(store.getMovements());
        ((TextView) findViewById(R.id.textInventoryValue)).setText(store.formatMoney(store.getInventoryValue()));
        ((TextView) findViewById(R.id.textInventoryAlerts)).setText(String.valueOf(store.getLowStockCount()));
    }
}
