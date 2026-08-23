package com.clarys.app;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.clarys.app.data.StoreCallback;
import com.clarys.app.data.SupabaseStore;
import com.clarys.app.model.OrderRequest;
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

        // Menú inferior desplegable
        setupBottomMenu();

        updateDashboardBottomPadding();

        // Navegación principal
        bindNavigation(
                R.id.buttonOpenProducts,
                ProductListActivity.class
        );

        bindNavigation(
                R.id.buttonOpenInventory,
                InventoryActivity.class
        );

        bindNavigation(
                R.id.buttonOpenReports,
                ReportsActivity.class
        );

        findViewById(R.id.buttonPendingOrders)
                .setOnClickListener(view -> openPendingOrders());

        // Opciones adicionales

        findViewById(R.id.buttonOpenCatalog)
                .setOnClickListener(view -> openAdminCatalog());

        bindNavigation(
                R.id.buttonOpenCustomers,
                CustomerActivity.class
        );

        bindNavigation(
                R.id.buttonOpenSettings,
                SettingsActivity.class
        );

        findViewById(R.id.buttonLogout)
                .setOnClickListener(view -> logout());
    }



    private void updateDashboardBottomPadding() {

        ScrollView scrollDashboard =
                findViewById(R.id.scrollDashboard);

        LinearLayout bottomMenu =
                findViewById(R.id.layoutDashboardBottomMenu);

        bottomMenu.post(() -> {

            int menuHeight = bottomMenu.getHeight();

            int extraSpace = dpToPx(16);

            scrollDashboard.setPadding(
                    scrollDashboard.getPaddingLeft(),
                    scrollDashboard.getPaddingTop(),
                    scrollDashboard.getPaddingRight(),
                    menuHeight + extraSpace
            );
        });
    }

    private int dpToPx(int dp) {
        return Math.round(
                dp * getResources().getDisplayMetrics().density
        );
    }

    // =========================================================
    // MENÚ INFERIOR
    // =========================================================

    private void setupBottomMenu() {

        LinearLayout buttonToggleDashboardMenu =
                findViewById(R.id.buttonToggleDashboardMenu);

        LinearLayout layoutDashboardMoreOptions =
                findViewById(R.id.layoutDashboardMoreOptions);

        TextView textToggleDashboardMenu =
                findViewById(R.id.textToggleDashboardMenu);

        ImageView iconToggleDashboardMenu =
                findViewById(R.id.iconToggleDashboardMenu);


        layoutDashboardMoreOptions.setVisibility(View.GONE);
        iconToggleDashboardMenu.setRotation(0f);


        buttonToggleDashboardMenu.setOnClickListener(view -> {

            boolean isExpanded =
                    layoutDashboardMoreOptions.getVisibility()
                            == View.VISIBLE;


            if (isExpanded) {

                // CERRAR

                iconToggleDashboardMenu.animate()
                        .rotation(0f)
                        .setDuration(180)
                        .start();

                textToggleDashboardMenu.setText("Más opciones");

                layoutDashboardMoreOptions.animate()
                        .alpha(0f)
                        .translationY(40f)
                        .setDuration(180)
                        .withEndAction(() -> {

                            layoutDashboardMoreOptions
                                    .setVisibility(View.GONE);

                            layoutDashboardMoreOptions
                                    .setAlpha(1f);

                            layoutDashboardMoreOptions
                                    .setTranslationY(0f);

                            // Recalcular espacio inferior
                            updateDashboardBottomPadding();
                        })
                        .start();

            } else {

                // ABRIR

                layoutDashboardMoreOptions
                        .setVisibility(View.VISIBLE);

                layoutDashboardMoreOptions
                        .setAlpha(0f);

                layoutDashboardMoreOptions
                        .setTranslationY(40f);


                // Esperamos a que Android mida
                // la nueva altura del menú.
                layoutDashboardMoreOptions.post(
                        this::updateDashboardBottomPadding
                );


                layoutDashboardMoreOptions.animate()
                        .alpha(1f)
                        .translationY(0f)
                        .setDuration(220)
                        .start();


                iconToggleDashboardMenu.animate()
                        .rotation(180f)
                        .setDuration(220)
                        .start();

                textToggleDashboardMenu
                        .setText("Menos opciones");
            }
        });
    }


    // =========================================================
    // REFRESH
    // =========================================================

    @Override
    protected void onResume() {
        super.onResume();

        renderDashboard();

        if (!store.isAuthenticated()) {
            return;
        }

        store.refreshProducts(
                true,
                new StoreCallback<java.util.List<Product>>() {

                    @Override
                    public void onSuccess(java.util.List<Product> result) {
                        refreshSalesForDashboard();
                    }

                    @Override
                    public void onError(String message) {
                        refreshSalesForDashboard();
                    }
                }
        );
    }

    private void refreshSalesForDashboard() {

        store.refreshSales(
                new StoreCallback<java.util.List<com.clarys.app.model.Sale>>() {

                    @Override
                    public void onSuccess(
                            java.util.List<com.clarys.app.model.Sale> sales) {

                        refreshOrdersForDashboard();
                    }

                    @Override
                    public void onError(String message) {

                        refreshOrdersForDashboard();
                    }
                }
        );
    }

    private void refreshOrdersForDashboard() {

        store.refreshOrderRequests(
                new StoreCallback<java.util.List<OrderRequest>>() {

                    @Override
                    public void onSuccess(
                            java.util.List<OrderRequest> result) {

                        renderDashboard();
                    }

                    @Override
                    public void onError(String message) {

                        renderDashboard();
                    }
                }
        );
    }

    private void openPendingOrders() {

        Intent intent =
                new Intent(
                        this,
                        ReportsActivity.class
                );

        intent.putExtra(
                "reportSection",
                "orders"
        );

        intent.putExtra(
                "requestStatus",
                OrderRequest.STATUS_PENDING
        );

        startActivity(intent);
    }


    // =========================================================
    // DASHBOARD
    // =========================================================

    private void renderDashboard() {

        Product product = store.getBestSeller();

        int salesTotal =
                store.getTodaySalesTotal();

        int orders =
                store.getPendingOrderCount();

        int lowStock =
                store.getLowStockCount();

        int products =
                store.getProducts().size();


        ((TextView) findViewById(
                R.id.textDashboardSales))
                .setText(
                        store.formatMoney(salesTotal)
                );


        ((TextView) findViewById(
                R.id.textDashboardOrders))
                .setText(
                        String.valueOf(orders)
                );


        ((TextView) findViewById(
                R.id.textDashboardLowStock))
                .setText(
                        String.valueOf(lowStock)
                );


        ((TextView) findViewById(
                R.id.textDashboardInventoryValue))
                .setText(
                        store.formatMoney(
                                store.getInventoryValue()
                        )
                );


        ((TextView) findViewById(
                R.id.textDashboardBestSeller))
                .setText(
                        product == null
                                ? "Sin ventas"
                                : product.getName()
                                + " | "
                                + product.getSoldUnits()
                                + " und"
                );


        chartDashboard.setMetrics(
                salesTotal,
                orders,
                lowStock,
                products
        );
    }


    // =========================================================
    // CATÁLOGO
    // =========================================================

    private void openAdminCatalog() {

        Intent intent =
                new Intent(
                        this,
                        CatalogActivity.class
                );

        intent.putExtra(
                "adminCatalog",
                true
        );

        startActivity(intent);
    }


    // =========================================================
    // LOGOUT
    // =========================================================

    private void logout() {

        store.signOut();

        Intent intent =
                new Intent(
                        this,
                        LoginActivity.class
                );

        intent.addFlags(
                Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_NEW_TASK
        );

        startActivity(intent);

        finish();
    }
}