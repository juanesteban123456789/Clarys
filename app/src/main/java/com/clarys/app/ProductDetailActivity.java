package com.clarys.app;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.clarys.app.data.SupabaseStore;
import com.clarys.app.model.Product;

public class ProductDetailActivity extends BaseScreenActivity {

    private SupabaseStore store;

    private Product product;

    private boolean adminCatalog;


    private View addToCartButton;

    private View backToCatalogButton;

    private View inventoryButton;

    private View editButton;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);


        setContentView(
                R.layout.activity_product_detail
        );


        store =
                SupabaseStore.getInstance(this);


        // =========================================================
        // CONTEXTO DE LA PANTALLA
        // =========================================================

        boolean requestedAdminCatalog =
                getIntent().getBooleanExtra(
                        "adminCatalog",
                        false
                );


        /*
         * Un detalle solamente puede considerarse administrativo
         * cuando se cumplen las tres condiciones:
         *
         * 1. Se solicitó desde un catálogo administrativo.
         * 2. La aplicación está actualmente en modo ADMIN.
         * 3. Existe una sesión administrativa válida.
         */
        adminCatalog =
                requestedAdminCatalog
                        && AppModeManager.isAdminMode(this)
                        && store.isAuthenticated();


        /*
         * Si alguien intenta abrir un detalle administrativo
         * sin permisos válidos, simplemente mostramos
         * el detalle como cliente.
         */
        if (requestedAdminCatalog && !adminCatalog) {

            AppModeManager.enterClientMode(this);
        }


        // =========================================================
        // HEADER
        // =========================================================

        setupHeader(
                R.id.buttonHeaderHome,
                R.id.buttonHeaderBack
        );


        // =========================================================
        // CONTROLES
        // =========================================================

        addToCartButton =
                findViewById(
                        R.id.buttonAddToCart
                );


        backToCatalogButton =
                findViewById(
                        R.id.buttonBackToCatalog
                );


        inventoryButton =
                findViewById(
                        R.id.buttonSeeInventory
                );


        editButton =
                findViewById(
                        R.id.buttonEditProduct
                );


        // =========================================================
        // AGREGAR AL CARRITO
        // =========================================================

        addToCartButton.setOnClickListener(view -> {

            /*
             * Nunca permitimos ejecutar esta acción desde
             * el contexto administrativo.
             */
            if (isAdminDetailMode()) {

                return;
            }


            if (product != null
                    && store.addToCart(
                    product.getId(),
                    1
            )) {

                showMessage(
                        "Producto agregado al carrito"
                );


                openScreen(
                        CartActivity.class
                );

            } else {

                showMessage(
                        "Producto sin stock disponible"
                );
            }
        });


        // =========================================================
        // VOLVER AL CATÁLOGO
        // =========================================================

        backToCatalogButton.setOnClickListener(view -> {

            Intent intent =
                    new Intent(
                            this,
                            CatalogActivity.class
                    );


            /*
             * Solamente devolvemos adminCatalog=true
             * si todavía seguimos realmente en ADMIN.
             */
            intent.putExtra(
                    "adminCatalog",
                    isAdminDetailMode()
            );


            startActivity(intent);

            finish();
        });


        // =========================================================
        // INVENTARIO
        // =========================================================

        inventoryButton.setOnClickListener(view -> {

            if (!isAdminDetailMode()) {

                showMessage(
                        "Acceso administrativo requerido"
                );

                return;
            }


            if (product == null) {

                return;
            }


            Intent intent =
                    new Intent(
                            this,
                            InventoryMovementActivity.class
                    );


            intent.putExtra(
                    "productId",
                    product.getId()
            );


            startActivity(intent);
        });


        // =========================================================
        // EDITAR PRODUCTO
        // =========================================================

        editButton.setOnClickListener(view -> {

            if (!isAdminDetailMode()) {

                showMessage(
                        "Acceso administrativo requerido"
                );

                return;
            }


            if (product == null) {

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


            startActivity(intent);
        });


        // =========================================================
        // PRIMER RENDER DE PERMISOS
        // =========================================================

        renderAccessControls();
    }


    // =============================================================
    // RESUME
    // =============================================================

    @Override
    protected void onResume() {
        super.onResume();


        /*
         * Si mientras estábamos fuera cambió la sesión o
         * el modo de la aplicación, volvemos a validar.
         */
        if (adminCatalog && !isAdminDetailMode()) {

            adminCatalog = false;

            AppModeManager.enterClientMode(this);
        }


        renderAccessControls();


        int productId =
                getIntent().getIntExtra(
                        "productId",
                        1
                );


        product =
                store.getProduct(productId);


        /*
         * Conservamos tu comportamiento anterior:
         *
         * si por algún motivo no encontramos exactamente
         * el producto solicitado pero existe una lista cargada,
         * mostramos el primero.
         */
        if (product == null
                && !store.getProducts().isEmpty()) {

            product =
                    store.getProducts().get(0);
        }


        renderProduct();
    }


    // =============================================================
    // PANTALLA PÚBLICA
    // =============================================================

    @Override
    protected boolean isPublicScreen() {

        /*
         * Un cliente puede consultar detalles
         * sin iniciar sesión.
         */
        return true;
    }


    // =============================================================
    // CONTEXTO ADMINISTRATIVO
    // =============================================================

    @Override
    protected boolean isAdminContext() {

        return isAdminDetailMode();
    }


    // =============================================================
    // COMPROBAR MODO ADMIN
    // =============================================================

    private boolean isAdminDetailMode() {

        return adminCatalog
                && AppModeManager.isAdminMode(this)
                && store != null
                && store.isAuthenticated();
    }


    // =============================================================
    // VISIBILIDAD DE CONTROLES
    // =============================================================

    private void renderAccessControls() {

        if (addToCartButton == null
                || inventoryButton == null
                || editButton == null) {

            return;
        }


        boolean adminMode =
                isAdminDetailMode();


        if (adminMode) {

            // =====================================================
            // ADMIN
            // =====================================================

            addToCartButton.setVisibility(
                    View.GONE
            );


            inventoryButton.setVisibility(
                    View.VISIBLE
            );


            editButton.setVisibility(
                    View.VISIBLE
            );

        } else {

            // =====================================================
            // CLIENTE
            // =====================================================

            addToCartButton.setVisibility(
                    View.VISIBLE
            );


            inventoryButton.setVisibility(
                    View.GONE
            );


            editButton.setVisibility(
                    View.GONE
            );
        }
    }


    // =============================================================
    // MOSTRAR PRODUCTO
    // =============================================================

    private void renderProduct() {

        if (product == null) {

            showMessage(
                    "Producto no encontrado"
            );

            return;
        }


        ((TextView) findViewById(
                R.id.textDetailName
        )).setText(
                product.getName()
        );


        ((TextView) findViewById(
                R.id.textDetailPrice
        )).setText(
                store.formatMoney(
                        product.getSalePrice()
                )
        );


        ((TextView) findViewById(
                R.id.textDetailDescription
        )).setText(
                product.getDescription()
        );


        ((TextView) findViewById(
                R.id.textDetailCategory
        )).setText(
                product.getCategory()
                        + " | SKU "
                        + product.getSku()
        );


        ((TextView) findViewById(
                R.id.textDetailSizes
        )).setText(
                product.getSizes()
        );


        ((TextView) findViewById(
                R.id.textDetailColors
        )).setText(
                product.getColors()
        );


        ((TextView) findViewById(
                R.id.textDetailStock
        )).setText(
                "Stock "
                        + product.getStock()
                        + " | mínimo "
                        + product.getMinStock()
        );


        // =========================================================
        // IMAGEN
        // =========================================================

        ImageView image =
                findViewById(
                        R.id.imageDetailProduct
                );


        String imageUrl =
                product.getImageUrl();


        if (imageUrl == null
                || imageUrl.trim().isEmpty()) {

            image.setImageResource(
                    android.R.color.transparent
            );

        } else {

            Glide.with(this)
                    .load(imageUrl)
                    .centerCrop()
                    .into(image);
        }
    }
}