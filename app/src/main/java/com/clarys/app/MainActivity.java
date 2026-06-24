package com.clarys.app;

import android.os.Bundle;
import android.widget.TextView;

import com.clarys.app.data.MockStore;
import com.clarys.app.model.Product;

public class MainActivity extends BaseScreenActivity {
    private final MockStore store = MockStore.getInstance();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bindNavigation(R.id.buttonOpenSale, SaleActivity.class);
        bindNavigation(R.id.buttonOpenProducts, ProductListActivity.class);
        bindNavigation(R.id.buttonOpenCatalog, CatalogActivity.class);
        bindNavigation(R.id.buttonOpenInventory, InventoryActivity.class);
        bindNavigation(R.id.buttonOpenReports, ReportsActivity.class);
        bindNavigation(R.id.buttonOpenCustomers, CustomerActivity.class);
        bindNavigation(R.id.buttonOpenSettings, SettingsActivity.class);
        bindNavigation(R.id.buttonOpenLogin, LoginActivity.class);
    }

    @Override
    protected void onResume() {
        super.onResume();
        renderDashboard();
    }

    private void renderDashboard() {
        Product product = store.getBestSeller();
        ((TextView) findViewById(R.id.textDashboardSales)).setText(store.formatMoney(store.getTodaySalesTotal()));
        ((TextView) findViewById(R.id.textDashboardOrders)).setText(String.valueOf(store.getSales().size()));
        ((TextView) findViewById(R.id.textDashboardLowStock)).setText(String.valueOf(store.getLowStockCount()));
        ((TextView) findViewById(R.id.textDashboardInventoryValue)).setText(store.formatMoney(store.getInventoryValue()));
        ((TextView) findViewById(R.id.textDashboardBestSeller)).setText(product == null
                ? "Sin ventas" : product.getName() + " | " + product.getSoldUnits() + " und");
    }
}
