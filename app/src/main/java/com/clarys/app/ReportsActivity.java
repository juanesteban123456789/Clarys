package com.clarys.app;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.clarys.app.data.MockStore;
import com.clarys.app.model.CartItem;
import com.clarys.app.model.Product;
import com.clarys.app.model.Sale;
import com.clarys.app.ui.SaleAdapter;

public class ReportsActivity extends BaseScreenActivity {
    private final MockStore store = MockStore.getInstance();
    private SaleAdapter saleAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reports);

        setupHeader(R.id.buttonHeaderHome, R.id.buttonHeaderBack);
        bindNavigation(R.id.buttonReportNewSale, SaleActivity.class);

        RecyclerView salesList = findViewById(R.id.recyclerSalesHistory);
        salesList.setLayoutManager(new LinearLayoutManager(this));
        saleAdapter = new SaleAdapter(sale -> {
            Intent intent = new Intent(this, SaleDetailActivity.class);
            intent.putExtra("saleId", sale.getId());
            startActivity(intent);
        });
        salesList.setAdapter(saleAdapter);
    }

    @Override
    protected void onResume() {
        super.onResume();
        renderReports();
    }

    private void renderReports() {
        Product bestSeller = store.getBestSeller();
        ((TextView) findViewById(R.id.textReportSales)).setText(store.formatMoney(store.getTodaySalesTotal()));
        ((TextView) findViewById(R.id.textReportProfit)).setText(store.formatMoney(calculateProfit()));
        ((TextView) findViewById(R.id.textReportInventoryValue)).setText(store.formatMoney(store.getInventoryValue()));
        ((TextView) findViewById(R.id.textReportLowStock)).setText(String.valueOf(store.getLowStockCount()));
        ((TextView) findViewById(R.id.textReportBestSeller)).setText(bestSeller == null
                ? "Sin datos" : bestSeller.getName() + " | " + bestSeller.getSoldUnits() + " und");
        saleAdapter.submitList(store.getSales());
    }

    private int calculateProfit() {
        int profit = 0;
        for (Sale sale : store.getSales()) {
            for (CartItem item : sale.getItems()) {
                profit += item.getProduct().getPotentialProfit() * item.getQuantity();
            }
            profit -= sale.getDiscount();
        }
        return Math.max(0, profit);
    }
}
