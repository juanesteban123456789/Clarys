package com.clarys.app;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.clarys.app.data.StoreCallback;
import com.clarys.app.data.SupabaseStore;
import com.clarys.app.model.Product;
import com.clarys.app.ui.ProductAdapter;


public class CatalogActivity extends BaseScreenActivity {

    private SupabaseStore store;

    private ProductAdapter adapter;

    private EditText searchInput;


    /**
     * true:
     * El catálogo está siendo utilizado dentro
     * del entorno administrativo.
     *
     * false:
     * El catálogo se comporta como lo vería
     * un cliente.
     */
    private boolean adminCatalog;


    // =============================================================
    // ON CREATE
    // =============================================================

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        setContentView(
                R.layout.activity_catalog
        );


        store =
                SupabaseStore.getInstance(this);


        // =========================================================
        // DETERMINAR CONTEXTO DEL CATÁLOGO
        // =========================================================

        boolean requestedAdminCatalog =
                getIntent().getBooleanExtra(
                        "adminCatalog",
                        false
                );


        /*
         * Para que el catálogo funcione como administrador
         * deben cumplirse:
         *
         * 1. Se solicitó adminCatalog.
         * 2. La aplicación está en modo ADMIN.
         * 3. Existe una sesión administrativa válida.
         */
        adminCatalog =
                requestedAdminCatalog
                        && AppModeManager.isAdminMode(this)
                        && store.isAuthenticated();


        /*
         * Si se intentó entrar al catálogo administrativo
         * sin una sesión/modo válido, volvemos al contexto cliente.
         */
        if (requestedAdminCatalog
                && !adminCatalog) {

            AppModeManager.enterClientMode(
                    this
            );
        }


        // =========================================================
        // HEADER
        // =========================================================

        setupHeader(
                R.id.buttonHeaderHome,
                R.id.buttonHeaderBack
        );


        // =========================================================
        // NAVEGACIÓN INFERIOR
        // =========================================================

        setupBottomNavigation();


        // =========================================================
        // VENTA RÁPIDA
        // =========================================================

        View saleButton =
                findViewById(
                        R.id.buttonGoToSale
                );


        if (saleButton != null) {

            saleButton.setOnClickListener(
                    view -> {

                        if (adminCatalog
                                && AppModeManager.isAdminMode(this)
                                && store.isAuthenticated()) {

                            openScreen(
                                    SaleActivity.class
                            );

                        } else {

                            showMessage(
                                    "Acceso administrativo requerido"
                            );
                        }
                    }
            );
        }


        // =========================================================
        // BÚSQUEDA VISUAL
        // =========================================================

        bindMessage(
                R.id.buttonSearchVisual,
                "Búsqueda visual disponible, lógica pendiente"
        );


        // =========================================================
        // BUSCADOR
        // =========================================================

        searchInput =
                findViewById(
                        R.id.inputCatalogSearch
                );


        View searchButton =
                findViewById(
                        R.id.buttonCatalogSearch
                );


        if (searchButton != null) {

            searchButton.setOnClickListener(
                    view ->
                            refreshProducts()
            );
        }


        // =========================================================
        // PRODUCTOS
        // =========================================================

        RecyclerView productsList =
                findViewById(
                        R.id.recyclerCatalogProducts
                );


        productsList.setLayoutManager(
                new LinearLayoutManager(this)
        );


        /*
         * CLIENTE
         *
         * Ver detalle | Agregar
         *
         *
         * ADMIN
         *
         * Ver detalle | Editar
         */
        adapter =
                new ProductAdapter(
                        "Ver detalle",

                        adminCatalog
                                ? "Editar"
                                : "Agregar",

                        adminCatalog,

                        new ProductAdapter.ProductActionListener() {

                            @Override
                            public void onPrimaryAction(
                                    Product product) {

                                openProductDetail(
                                        product.getId()
                                );
                            }


                            @Override
                            public void onSecondaryAction(
                                    Product product) {

                                // =================================
                                // ADMINISTRADOR
                                // =================================

                                if (adminCatalog) {

                                    openProductEditor(
                                            product
                                    );

                                    return;
                                }


                                // =================================
                                // CLIENTE
                                // =================================

                                addProductToCart(
                                        product
                                );
                            }
                        }
                );


        productsList.setAdapter(
                adapter
        );
    }


    // =============================================================
    // NAVEGACIÓN INFERIOR
    // =============================================================

    private void setupBottomNavigation() {

        View bottomCatalog =
                findViewById(
                        R.id.buttonBottomCategories
                );


        View bottomCart =
                findViewById(
                        R.id.buttonBottomCart
                );


        View bottomOrders =
                findViewById(
                        R.id.buttonBottomOrders
                );


        View bottomMore =
                findViewById(
                        R.id.buttonBottomProfile
                );


        // =========================================================
        // CATÁLOGO
        // =========================================================

        if (bottomCatalog != null) {

            bottomCatalog.setOnClickListener(
                    view -> {

                        /*
                         * Ya estamos en CatalogActivity.
                         *
                         * No abrimos otra instancia.
                         */
                    }
            );
        }


        // =========================================================
        // CARRITO / PRODUCTOS
        // =========================================================

        if (bottomCart != null) {

            if (adminCatalog) {

                /*
                 * En contexto administrador, esta posición
                 * continúa funcionando como acceso a productos.
                 */
                bottomCart.setOnClickListener(
                        view ->
                                openScreen(
                                        ProductListActivity.class
                                )
                );

            } else {

                /*
                 * Cliente:
                 * abrir carrito.
                 */
                bottomCart.setOnClickListener(
                        view ->
                                openScreen(
                                        CartActivity.class
                                )
                );
            }
        }


        // =========================================================
        // MIS PEDIDOS
        // =========================================================

        if (bottomOrders != null) {

            if (adminCatalog) {

                /*
                 * En modo administrador reutilizamos esta posición
                 * para entrar a la gestión de pedidos.
                 */
                bottomOrders.setOnClickListener(
                        view ->
                                openAdminOrders()
                );

            } else {

                /*
                 * Cliente:
                 *
                 * Consulta de pedidos por teléfono.
                 */
                bottomOrders.setOnClickListener(
                        view ->
                                openScreen(
                                        ClientOrdersActivity.class
                                )
                );
            }
        }


        // =========================================================
        // MÁS
        // =========================================================

        if (bottomMore != null) {

            bottomMore.setVisibility(
                    View.VISIBLE
            );


            bottomMore.setOnClickListener(
                    view ->
                            showMoreMenu()
            );
        }
    }


    // =============================================================
    // PEDIDOS ADMINISTRATIVOS
    // =============================================================

    private void openAdminOrders() {

        /*
         * Doble validación para evitar abrir una pantalla
         * administrativa únicamente porque el botón esté visible.
         */
        if (!adminCatalog
                || !AppModeManager.isAdminMode(this)
                || !store.isAuthenticated()) {

            showMessage(
                    "Acceso administrativo requerido"
            );

            return;
        }


        Intent intent =
                new Intent(
                        this,
                        ReportsActivity.class
                );


        /*
         * ReportsActivity ya puede utilizar estos valores
         * para abrir directamente la sección de pedidos.
         */
        intent.putExtra(
                "reportSection",
                "orders"
        );


        intent.putExtra(
                "requestStatus",
                "pending"
        );


        startActivity(
                intent
        );
    }


    // =============================================================
    // MENÚ "MÁS"
    // =============================================================

    private void showMoreMenu() {

        String[] options = {

                "Cambiar modo",
                "Acerca de Clarys"
        };


        new AlertDialog.Builder(this)

                .setTitle(
                        "Más opciones"
                )

                .setItems(
                        options,

                        (dialog, which) -> {

                            if (which == 0) {

                                showModeSelector();

                            } else if (which == 1) {

                                showAbout();
                            }
                        }
                )

                .setNegativeButton(
                        "Cancelar",
                        null
                )

                .show();
    }


    // =============================================================
    // SELECTOR CLIENTE / ADMINISTRACIÓN
    // =============================================================

    private void showModeSelector() {

        String[] modes = {

                "Continuar como cliente",
                "Administración"
        };


        new AlertDialog.Builder(this)

                .setTitle(
                        "Cambiar modo"
                )

                .setItems(
                        modes,

                        (dialog, which) -> {

                            if (which == 0) {

                                enterClientMode();

                            } else if (which == 1) {

                                enterAdminMode();
                            }
                        }
                )

                .setNegativeButton(
                        "Cancelar",
                        null
                )

                .show();
    }


    // =============================================================
    // MODO CLIENTE
    // =============================================================

    private void enterClientMode() {

        /*
         * Si ya estamos completamente en modo cliente,
         * no necesitamos reiniciar la Activity.
         */
        if (!adminCatalog
                && AppModeManager.isClientMode(this)) {

            return;
        }


        /*
         * IMPORTANTE:
         *
         * Esto NO cierra la sesión administrativa.
         *
         * El administrador puede navegar como cliente
         * y después regresar al panel sin iniciar sesión
         * nuevamente.
         */
        AppModeManager.enterClientMode(
                this
        );


        Intent intent =
                new Intent(
                        this,
                        CatalogActivity.class
                );


        intent.putExtra(
                "adminCatalog",
                false
        );


        /*
         * Evitamos que Back pueda regresar accidentalmente
         * al contexto administrativo anterior.
         */
        intent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TASK
        );


        startActivity(
                intent
        );


        finish();
    }


    // =============================================================
    // MODO ADMINISTRADOR
    // =============================================================

    private void enterAdminMode() {

        /*
         * Ya existe una sesión administrativa.
         */
        if (store.isAuthenticated()) {

            AppModeManager.enterAdminMode(
                    this
            );


            openAdminDashboard();

            return;
        }


        /*
         * No existe sesión.
         *
         * Abrimos directamente el acceso administrativo.
         */
        Intent intent =
                new Intent(
                        this,
                        AdminAccessActivity.class
                );


        startActivity(
                intent
        );
    }


    // =============================================================
    // DASHBOARD ADMINISTRATIVO
    // =============================================================

    private void openAdminDashboard() {

        Intent intent =
                new Intent(
                        this,
                        MainActivity.class
                );


        intent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TASK
        );


        startActivity(
                intent
        );


        finish();
    }


    // =============================================================
    // ACERCA DE
    // =============================================================

    private void showAbout() {

        new AlertDialog.Builder(this)

                .setTitle(
                        "Clarys"
                )

                .setMessage(
                        "Catálogo y gestión para tu taller."
                )

                .setPositiveButton(
                        "Aceptar",
                        null
                )

                .show();
    }


    // =============================================================
    // SEGURIDAD / CONTEXTO
    // =============================================================

    @Override
    protected boolean isPublicScreen() {

        /*
         * CatalogActivity puede verse sin autenticación.
         */
        return true;
    }


    @Override
    protected boolean isAdminContext() {

        /*
         * Tener una sesión administrativa no significa
         * necesariamente estar usando la interfaz administrativa.
         */
        return adminCatalog
                && AppModeManager.isAdminMode(this)
                && store != null
                && store.isAuthenticated();
    }


    // =============================================================
    // ON RESUME
    // =============================================================

    @Override
    protected void onResume() {

        super.onResume();


        /*
         * Puede ocurrir que mientras esta Activity estaba pausada
         * haya cambiado el estado de la sesión.
         */
        if (adminCatalog
                && (
                !store.isAuthenticated()
                        || !AppModeManager.isAdminMode(this)
        )) {

            adminCatalog = false;


            AppModeManager.enterClientMode(
                    this
            );
        }


        renderAccessControls();


        /*
         * adminCatalog = true
         *
         * Carga productos administrativos del taller.
         *
         *
         * adminCatalog = false
         *
         * Carga únicamente el catálogo público.
         */
        store.refreshProducts(
                adminCatalog,

                new StoreCallback<java.util.List<Product>>() {

                    @Override
                    public void onSuccess(
                            java.util.List<Product> result) {

                        refreshProducts();
                    }


                    @Override
                    public void onError(
                            String message) {

                        showMessage(
                                message
                        );


                        refreshProducts();
                    }
                }
        );
    }


    // =============================================================
    // AJUSTAR INTERFAZ SEGÚN MODO
    // =============================================================

    private void renderAccessControls() {

        View saleButton =
                findViewById(
                        R.id.buttonGoToSale
                );


        TextView catalogButton =
                findViewById(
                        R.id.buttonBottomCategories
                );


        TextView cartButton =
                findViewById(
                        R.id.buttonBottomCart
                );


        TextView ordersButton =
                findViewById(
                        R.id.buttonBottomOrders
                );


        TextView moreButton =
                findViewById(
                        R.id.buttonBottomProfile
                );


        // =========================================================
        // CATÁLOGO
        // =========================================================

        if (catalogButton != null) {

            catalogButton.setText(
                    "Catálogo"
            );
        }


        // =========================================================
        // MÁS
        // =========================================================

        if (moreButton != null) {

            moreButton.setVisibility(
                    View.VISIBLE
            );


            moreButton.setText(
                    "Más"
            );


            /*
             * Conservamos el icono de tres puntos.
             */
            moreButton.setCompoundDrawablesRelativeWithIntrinsicBounds(
                    0,
                    R.drawable.ic_more_horiz,
                    0,
                    0
            );
        }


        // =========================================================
        // MODO ADMIN
        // =========================================================

        if (adminCatalog) {

            if (saleButton != null) {

                saleButton.setVisibility(
                        View.VISIBLE
                );
            }


            if (cartButton != null) {

                cartButton.setText(
                        "Productos"
                );
            }


            if (ordersButton != null) {

                ordersButton.setText(
                        "Pedidos"
                );
            }


            return;
        }


        // =========================================================
        // MODO CLIENTE
        // =========================================================

        if (saleButton != null) {

            saleButton.setVisibility(
                    View.GONE
            );
        }


        if (cartButton != null) {

            cartButton.setText(
                    "Carrito"
            );
        }


        if (ordersButton != null) {

            ordersButton.setText(
                    "Mis pedidos"
            );
        }
    }


    // =============================================================
    // FILTRAR PRODUCTOS
    // =============================================================

    private void refreshProducts() {

        if (adapter == null
                || searchInput == null) {

            return;
        }


        adapter.submitList(
                store.searchProducts(

                        searchInput
                                .getText()
                                .toString(),

                        "Todos"
                )
        );
    }


    // =============================================================
    // DETALLE PRODUCTO
    // =============================================================

    private void openProductDetail(
            int productId) {

        Intent intent =
                new Intent(
                        this,
                        ProductDetailActivity.class
                );


        intent.putExtra(
                "productId",
                productId
        );


        /*
         * ProductDetailActivity necesita saber
         * en qué contexto fue abierta.
         */
        intent.putExtra(
                "adminCatalog",
                adminCatalog
        );


        startActivity(
                intent
        );
    }


    // =============================================================
    // EDITAR PRODUCTO
    // =============================================================

    private void openProductEditor(
            Product product) {

        /*
         * Doble comprobación para impedir acciones
         * administrativas fuera del modo ADMIN.
         */
        if (!adminCatalog
                || !AppModeManager.isAdminMode(this)
                || !store.isAuthenticated()) {

            showMessage(
                    "Acceso administrativo requerido"
            );

            return;
        }


        Intent intent =
                new Intent(
                        this,
                        ProductFormActivity.class
                );


        intent.putExtra(
                "productId",
                product.getId()
        );


        startActivity(
                intent
        );
    }


    // =============================================================
    // AGREGAR AL CARRITO
    // =============================================================

    private void addProductToCart(
            Product product) {

        /*
         * Una acción del cliente nunca debe ejecutarse
         * desde el catálogo administrativo.
         */
        if (adminCatalog) {

            return;
        }


        if (store.addToCart(
                product.getId(),
                1
        )) {

            showMessage(
                    "Producto agregado al carrito"
            );

        } else {

            showMessage(
                    "Producto sin stock disponible"
            );
        }
    }
}