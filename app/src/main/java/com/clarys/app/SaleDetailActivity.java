package com.clarys.app;

import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.clarys.app.data.SupabaseStore;
import com.clarys.app.model.CartItem;
import com.clarys.app.model.Sale;

public class SaleDetailActivity extends BaseScreenActivity {
    private SupabaseStore store;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sale_detail);
        store = SupabaseStore.getInstance(this);

        setupHeader(R.id.buttonHeaderHome, R.id.buttonHeaderBack);
        bindNavigation(R.id.buttonNewSaleFromDetail, SaleActivity.class);
        bindNavigation(R.id.buttonReportsFromDetail, ReportsActivity.class);
        renderSale();
    }

    private void renderSale() {
        int saleId = getIntent().getIntExtra("saleId", -1);
        Sale sale = saleId == -1 && !store.getSales().isEmpty()
                ? store.getSales().get(0)
                : store.getSale(saleId);
        if (sale == null) {
            ((TextView) findViewById(R.id.textSaleDetailTitle)).setText("Venta no encontrada");
            return;
        }

        ((TextView) findViewById(R.id.textSaleDetailTitle)).setText("Venta #" + sale.getId());
        ((TextView) findViewById(R.id.textSaleDetailCustomer)).setText(sale.getCustomerName()
                + " | " + sale.getCustomerPhone());
        ((TextView) findViewById(R.id.textSaleDetailStatus)).setText(sale.getStatus()
                + " | " + sale.getPaymentMethod());
        ((TextView) findViewById(R.id.textSaleDetailTotal)).setText("Total "
                + store.formatMoney(sale.getTotal()));

        LinearLayout itemsContainer = findViewById(R.id.containerSaleDetailItems);
        itemsContainer.removeAllViews();
        for (CartItem item : sale.getItems()) {
            TextView itemText = new TextView(this);
            itemText.setText(item.getProduct().getName() + " x" + item.getQuantity()
                    + " | " + store.formatMoney(item.getSubtotal()));
            itemText.setTextColor(getColor(R.color.clarys_text));
            itemText.setTextSize(15);
            itemText.setPadding(0, 8, 0, 8);
            itemsContainer.addView(itemText);
        }
    }
}
