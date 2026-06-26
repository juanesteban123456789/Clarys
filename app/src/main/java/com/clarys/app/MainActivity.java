package com.clarys.app;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import com.clarys.app.data.StoreCallback;
import com.clarys.app.data.SupabaseStore;
import com.clarys.app.model.Product;
import com.clarys.app.ui.DashboardChartView;

public class MainActivity extends BaseScreenActivity {
    private SupabaseStore store;
    private DashboardChartView chartDashboard;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = SupabaseStore.getInstance(this);
        if (!store.isAuthenticated()) {
            openScreen(LoginActivity.class);
            finish();
            return;
        }

        setContentView(R.layout.activity_main);
        chartDashboard = findViewById(R.id.chartDashboard);

        bindNavigation(R.id.buttonOpenProducts, ProductListActivity.class);
        findViewById(R.id.buttonOpenCatalog).setOnClickListener(view -> openAdminCatalog());
        bindNavigation(R.id.buttonOpenInventory, InventoryActivity.class);
        bindNavigation(R.id.buttonOpenReports, ReportsActivity.class);
        bindNavigation(R.id.buttonOpenCustomers, CustomerActivity.class);
        bindNavigation(R.id.buttonOpenSettings, SettingsActivity.class);
        findViewById(R.id.buttonLogout).setOnClickListener(view -> logout());
    }

    @Override
    protected void onResume() {
        super.onResume();
        renderDashboard();
        if (store.isAuthenticated()) {
            store.refreshProducts(true, new StoreCallback<java.util.List<Product>>() {
                @Override
                public void onSuccess(java.util.List<Product> result) {
                    store.refreshSales(new StoreCallback<java.util.List<com.clarys.app.model.Sale>>() {
                        @Override
                        public void onSuccess(java.util.List<com.clarys.app.model.Sale> sales) {
                            renderDashboard();
                        }

                        @Override
                        public void onError(String message) {
                            renderDashboard();
                        }
                    });
                }

                @Override
                public void onError(String message) {
                    renderDashboard();
                }
            });
        }
    }

    private void renderDashboard() {
        Product product = store.getBestSeller();
        int salesTotal = store.getTodaySalesTotal();
        int orders = store.getSales().size();
        int lowStock = store.getLowStockCount();
        int products = store.getProducts().size();
        ((TextView) findViewById(R.id.textDashboardSales)).setText(store.formatMoney(salesTotal));
        ((TextView) findViewById(R.id.textDashboardOrders)).setText(String.valueOf(orders));
        ((TextView) findViewById(R.id.textDashboardLowStock)).setText(String.valueOf(lowStock));
        ((TextView) findViewById(R.id.textDashboardInventoryValue)).setText(store.formatMoney(store.getInventoryValue()));
        ((TextView) findViewById(R.id.textDashboardBestSeller)).setText(product == null
                ? "Sin ventas" : product.getName() + " | " + product.getSoldUnits() + " und");
        chartDashboard.setMetrics(salesTotal, orders, lowStock, products);
    }

    private void openAdminCatalog() {
        Intent intent = new Intent(this, CatalogActivity.class);
        intent.putExtra("adminCatalog", true);
        startActivity(intent);
    }

    private void logout() {
        store.signOut();
        Intent intent = new Intent(this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }
}
