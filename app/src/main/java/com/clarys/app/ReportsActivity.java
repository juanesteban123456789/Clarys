package com.clarys.app;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.AdapterView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import com.clarys.app.util.SalesPeriod;
import com.clarys.app.util.CsvExporter;

import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.clarys.app.data.StoreCallback;
import com.clarys.app.data.SupabaseStore;
import com.clarys.app.model.CartItem;
import com.clarys.app.model.OrderRequest;
import com.clarys.app.model.Product;
import com.clarys.app.model.Sale;
import com.clarys.app.ui.OrderRequestAdapter;
import com.clarys.app.ui.SaleAdapter;

import java.util.List;
import java.util.ArrayList;
import com.clarys.app.util.OrderStatusUtils;

public class ReportsActivity extends BaseScreenActivity {

    private static final String FILTER_ALL = "all";
    public static final String FILTER_ACTIVE = "active";

    private SupabaseStore store;

    private SaleAdapter saleAdapter;

    private OrderRequestAdapter orderAdapter;

    private String selectedOrderStatus =
            FILTER_ALL;

    private boolean scrollToOrders;
    private int selectedPeriod;
    private String pendingExport = "";
    private final ActivityResultLauncher<String> exportFile =
            registerForActivityResult(new ActivityResultContracts.CreateDocument("text/csv"),uri -> {
                if(uri==null) { return; }
                try(java.io.OutputStream stream=getContentResolver().openOutputStream(uri)) {
                    if(stream==null) { throw new java.io.IOException("Archivo no disponible"); }
                    stream.write(pendingExport.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    showMessage("Archivo exportado");
                } catch(java.io.IOException error) { showMessage("No se pudo guardar el archivo"); }
            });


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(
                R.layout.activity_reports
        );

        store =
                SupabaseStore.getInstance(this);

        String notificationWorkshop = getIntent().getStringExtra("notificationWorkshopId");
        if (notificationWorkshop != null && !notificationWorkshop.equals(store.getWorkshopId())) {
            openScreen(MainActivity.class);
            finish();
            return;
        }


        setupHeader(
                R.id.buttonHeaderHome,
                R.id.buttonHeaderBack
        );


        bindNavigation(
                R.id.buttonReportNewSale,
                SaleActivity.class
        );


        // =========================================================
        // FILTRO RECIBIDO DESDE DASHBOARD
        // =========================================================

        String requestedStatus =
                getIntent().getStringExtra(
                        "requestStatus"
                );

        if (isValidOrderFilter(requestedStatus)) {

            selectedOrderStatus =
                    requestedStatus;
        }


        scrollToOrders =
                "orders".equals(
                        getIntent().getStringExtra(
                                "reportSection"
                        )
                );


        // =========================================================
        // VENTAS
        // =========================================================

        RecyclerView salesList =
                findViewById(
                        R.id.recyclerSalesHistory
                );

        salesList.setLayoutManager(
                new LinearLayoutManager(this)
        );


        saleAdapter =
                new SaleAdapter(sale -> {

                    Intent intent =
                            new Intent(
                                    this,
                                    SaleDetailActivity.class
                            );

                    intent.putExtra(
                            "saleId",
                            sale.getId()
                    );

                    startActivity(intent);
                });


        salesList.setAdapter(
                saleAdapter
        );


        // =========================================================
        // PEDIDOS
        // =========================================================

        RecyclerView ordersList =
                findViewById(
                        R.id.recyclerOrderRequests
                );

        ordersList.setLayoutManager(
                new LinearLayoutManager(this)
        );


        orderAdapter =
                new OrderRequestAdapter(
                        order -> {

                            Intent intent =
                                    new Intent(
                                            ReportsActivity.this,
                                            OrderRequestDetailActivity.class
                                    );

                            intent.putExtra(
                                    "orderId",
                                    order.getId()
                            );

                            startActivity(intent);
                        }
                );


        ordersList.setAdapter(
                orderAdapter
        );


        setupOrderFilters();
        Spinner period = findViewById(R.id.spinnerReportPeriod);
        period.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,
                new String[]{"Todas las fechas","Hoy","Esta semana","Este mes"}));
        period.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent,View view,int position,long id) {
                selectedPeriod=position; renderReports();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });
        findViewById(R.id.buttonExportSales).setOnClickListener(view -> {
            pendingExport=CsvExporter.sales(periodSales());
            exportFile.launch("Clarys_ventas.csv");
        });
        findViewById(R.id.buttonExportInventory).setOnClickListener(view -> {
            pendingExport=CsvExporter.inventory(store.getProducts());
            exportFile.launch("Clarys_inventario.csv");
        });
    }


    // =============================================================
    // FILTROS
    // =============================================================

    private void setupOrderFilters() {

        findViewById(R.id.filterOrderActive).setOnClickListener(view -> selectOrderFilter(FILTER_ACTIVE));

        findViewById(R.id.filterOrderAll)
                .setOnClickListener(view ->
                        selectOrderFilter(
                                FILTER_ALL
                        )
                );


        findViewById(R.id.filterOrderPending)
                .setOnClickListener(view ->
                        selectOrderFilter(
                                OrderRequest.STATUS_PENDING
                        )
                );

        findViewById(R.id.filterOrderContacted)
                .setOnClickListener(view ->
                        selectOrderFilter(
                                OrderRequest.STATUS_CONTACTED
                        )
                );


        findViewById(R.id.filterOrderApproved)
                .setOnClickListener(view ->
                        selectOrderFilter(
                                OrderRequest.STATUS_APPROVED
                        )
                );

        findViewById(R.id.filterOrderPreparing)
                .setOnClickListener(view ->
                        selectOrderFilter(
                                OrderRequest.STATUS_PREPARING
                        )
                );


        findViewById(R.id.filterOrderRejected)
                .setOnClickListener(view ->
                        selectOrderFilter(
                                OrderRequest.STATUS_REJECTED
                        )
                );


        findViewById(R.id.filterOrderCompleted)
                .setOnClickListener(view ->
                        selectOrderFilter(
                                OrderRequest.STATUS_COMPLETED
                        )
                );


        renderFilterStyles();
    }


    private void selectOrderFilter(
            String status) {

        selectedOrderStatus =
                status;

        renderFilterStyles();

        renderOrders();
    }


    private void renderFilterStyles() {

        styleFilter(R.id.filterOrderActive, FILTER_ACTIVE.equals(selectedOrderStatus));

        styleFilter(
                R.id.filterOrderAll,
                FILTER_ALL.equals(
                        selectedOrderStatus
                )
        );

        styleFilter(
                R.id.filterOrderPending,
                OrderRequest.STATUS_PENDING.equals(
                        selectedOrderStatus
                )
        );

        styleFilter(
                R.id.filterOrderContacted,
                OrderRequest.STATUS_CONTACTED.equals(
                        selectedOrderStatus
                )
        );

        styleFilter(
                R.id.filterOrderApproved,
                OrderRequest.STATUS_APPROVED.equals(
                        selectedOrderStatus
                )
        );

        styleFilter(
                R.id.filterOrderPreparing,
                OrderRequest.STATUS_PREPARING.equals(
                        selectedOrderStatus
                )
        );

        styleFilter(
                R.id.filterOrderRejected,
                OrderRequest.STATUS_REJECTED.equals(
                        selectedOrderStatus
                )
        );

        styleFilter(
                R.id.filterOrderCompleted,
                OrderRequest.STATUS_COMPLETED.equals(
                        selectedOrderStatus
                )
        );
    }


    private void styleFilter(
            int viewId,
            boolean selected) {

        TextView view =
                findViewById(viewId);


        view.setBackgroundResource(
                selected
                        ? R.drawable.bg_product_category
                        : R.drawable.bg_admin_action
        );


        view.setTextColor(
                getColor(
                        selected
                                ? R.color.clarys_mid
                                : R.color.clarys_muted
                )
        );
    }


    // =============================================================
    // REFRESH
    // =============================================================

    @Override
    protected void onResume() {
        super.onResume();

        refreshReportData();
    }


    private void refreshReportData() {

        store.refreshProducts(
                true,
                new StoreCallback<List<Product>>() {

                    @Override
                    public void onSuccess(
                            List<Product> result) {

                        refreshSales();
                    }

                    @Override
                    public void onError(
                            String message) {

                        refreshSales();
                    }
                }
        );
    }


    private void refreshSales() {

        store.refreshSales(
                new StoreCallback<List<Sale>>() {

                    @Override
                    public void onSuccess(
                            List<Sale> result) {

                        refreshOrders();
                    }

                    @Override
                    public void onError(
                            String message) {

                        refreshOrders();
                    }
                }
        );
    }


    private void refreshOrders() {

        store.refreshOrderRequests(
                new StoreCallback<List<OrderRequest>>() {

                    @Override
                    public void onSuccess(
                            List<OrderRequest> result) {

                        renderReports();

                        scrollToOrdersIfNeeded();
                    }

                    @Override
                    public void onError(
                            String message) {

                        showMessage(message);

                        renderReports();
                    }
                }
        );
    }


    // =============================================================
    // RENDER GENERAL
    // =============================================================

    private void renderReports() {

        Product bestSeller =
                store.getBestSeller();


        ((TextView) findViewById(
                R.id.textReportSales
        )).setText(
                store.formatMoney(
                        periodSalesTotal()
                )
        );


        ((TextView) findViewById(
                R.id.textReportProfit
        )).setText(
                store.formatMoney(
                        calculateProfit()
                )
        );


        ((TextView) findViewById(
                R.id.textReportInventoryValue
        )).setText(
                store.formatMoney(
                        store.getInventoryValue()
                )
        );


        ((TextView) findViewById(
                R.id.textReportLowStock
        )).setText(
                String.valueOf(
                        store.getLowStockCount()
                )
        );


        ((TextView) findViewById(
                R.id.textReportBestSeller
        )).setText(
                bestSeller == null
                        ? "Sin datos"
                        : bestSeller.getName()
                        + " | "
                        + bestSeller.getSoldUnits()
                        + " und"
        );


        saleAdapter.submitList(
                periodSales()
        );


        renderOrders();
    }


    // =============================================================
    // PEDIDOS
    // =============================================================

    private void renderOrders() {

        List<OrderRequest> filtered;
        if (FILTER_ACTIVE.equals(selectedOrderStatus)) {
            filtered = new ArrayList<>();
            for (OrderRequest order : store.getOrderRequests()) {
                if (OrderStatusUtils.isActive(order.getStatus())) { filtered.add(order); }
            }
        } else {
            filtered = store.getOrderRequestsByStatus(selectedOrderStatus);
        }


        orderAdapter.submitList(
                filtered
        );


        TextView pendingCount =
                findViewById(
                        R.id.textReportPendingOrders
                );


        int pending =
                store.getPendingOrderCount();


        pendingCount.setText(
                pending
                        + (pending == 1
                        ? " nuevo"
                        : " nuevos")
        );


        View empty =
                findViewById(
                        R.id.textOrderRequestsEmpty
                );


        empty.setVisibility(
                filtered.isEmpty()
                        ? View.VISIBLE
                        : View.GONE
        );
    }


    // =============================================================
    // SCROLL DESDE DASHBOARD
    // =============================================================

    private void scrollToOrdersIfNeeded() {

        if (!scrollToOrders) {
            return;
        }


        ScrollView scroll =
                findViewById(
                        R.id.scrollReports
                );


        View section =
                findViewById(
                        R.id.layoutOrderRequestsSection
                );


        scroll.post(() ->
                scroll.smoothScrollTo(
                        0,
                        section.getTop()
                )
        );


        scrollToOrders = false;
    }


    // =============================================================
    // GANANCIA
    // =============================================================

    private long calculateProfit() {

        long profit = 0;


        for (Sale sale :
                periodSales()) {

            for (CartItem item :
                    sale.getItems()) {

                profit +=
                        ((long)item.getProduct().getSalePrice() - item.getProduct().getPurchasePrice())
                                * item.getQuantity();
            }


            profit -=
                    sale.getDiscount();
        }


        return profit;
    }


    private boolean isValidOrderFilter(
            String status) {

        return FILTER_ALL.equals(status)

                || FILTER_ACTIVE.equals(status)

                || OrderRequest.STATUS_PENDING.equals(status)

                || OrderRequest.STATUS_CONTACTED.equals(status)

                || OrderRequest.STATUS_APPROVED.equals(status)

                || OrderRequest.STATUS_PREPARING.equals(status)

                || OrderRequest.STATUS_REJECTED.equals(status)

                || OrderRequest.STATUS_COMPLETED.equals(status);
    }

    private List<Sale> periodSales() {
        return SalesPeriod.filter(store.getSales(),selectedPeriod,System.currentTimeMillis(),java.util.TimeZone.getDefault());
    }
    private long periodSalesTotal() {
        long total=0;
        for(Sale sale:periodSales()) { total+=sale.getTotal(); }
        return total;
    }
}
