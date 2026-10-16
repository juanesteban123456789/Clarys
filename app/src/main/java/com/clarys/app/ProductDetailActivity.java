package com.clarys.app;

import android.content.Intent;
import android.content.ActivityNotFoundException;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.clarys.app.data.SupabaseStore;
import com.clarys.app.model.Product;
import com.clarys.app.model.ProductVariant;
import com.clarys.app.ui.VariantPicker;
import com.clarys.app.ui.CatalogProductAdapter;
import com.clarys.app.util.ProductSharing;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.LinearLayoutManager;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ProductDetailActivity extends BaseScreenActivity {

    private SupabaseStore store;

    private Product product;

    private boolean adminCatalog;

    private boolean openedFromAdmin;


    private View addToCartButton;

    private View backToCatalogButton;

    private View inventoryButton;

    private View editButton;

    private final ExecutorService shareExecutor = Executors.newSingleThreadExecutor();
    private boolean preparingShare;


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

        openedFromAdmin =
                BuildConfig.IS_ADMIN_APP
                        && getIntent().getBooleanExtra(
                        "openedFromAdmin",
                        false
                )
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


            if (product != null) {
                VariantPicker.show(this, product, variantId -> {
                    if (store.addToCart(product.getId(), variantId, 1)) {
                        showMessage("Producto agregado al carrito");
                        Intent intent = new Intent(this, CartActivity.class);
                        intent.putExtra(CatalogActivity.EXTRA_OPENED_FROM_ADMIN, openedFromAdmin);
                        startActivity(intent);
                    } else {
                        showMessage("Sin unidades disponibles o el carrito pertenece a otro taller");
                    }
                });
            }
        });


        // =========================================================
        // VOLVER AL CATÁLOGO
        // =========================================================

        backToCatalogButton.setOnClickListener(view -> navigateHome());
        ((TextView) backToCatalogButton).setText(BuildConfig.IS_ADMIN_APP
                ? R.string.navigation_back_dashboard : R.string.navigation_back_catalog);


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
        if (product == null) {
            showMessage("Producto no disponible");
            finish();
            return;
        }


        renderProduct();
        renderExtras();
    }

    private void renderExtras() {
        StringBuilder stock = new StringBuilder();
        stock.append("Total ").append(product.getTotalStock())
                .append(" · reservado ").append(product.getReservedStock())
                .append(" · disponible ").append(product.getStock());
        for (ProductVariant variant : product.getVariants()) {
            stock.append("\n").append(variant.getLabel()).append(": ")
                    .append(variant.getAvailableStock()).append(" disponibles");
        }
        ((TextView)findViewById(R.id.textDetailVariantStock)).setText(stock);
        View favorite = findViewById(R.id.buttonFavoriteProduct);
        ((ImageView)favorite).setImageResource(store.isFavorite(product.getId())
                ? android.R.drawable.btn_star_big_on : android.R.drawable.btn_star_big_off);
        favorite.setOnClickListener(view -> { store.toggleFavorite(product.getId()); renderExtras(); });
        View shareButton = findViewById(R.id.buttonShareProduct);
        shareButton.setEnabled(!preparingShare);
        shareButton.setOnClickListener(this::shareProduct);
        View whatsapp = findViewById(R.id.buttonProductWhatsapp);
        whatsapp.setVisibility(product.getWorkshopWhatsapp().isEmpty() ? View.GONE : View.VISIBLE);
        whatsapp.setOnClickListener(view -> {
            if (!WhatsAppUtils.openChat(this,product.getWorkshopWhatsapp(),
                    "Hola, me interesa "+product.getName()+" (código "+product.getInternalCode()
                            +"), precio "+store.formatMoney(product.getSalePrice())+".")) {
                showMessage("No se pudo abrir WhatsApp");
            }
        });
        View duplicate = findViewById(R.id.buttonDuplicateProduct);
        duplicate.setVisibility(isAdminDetailMode() ? View.VISIBLE : View.GONE);
        View promotion = findViewById(R.id.buttonPreparePromotion);
        promotion.setVisibility(BuildConfig.IS_ADMIN_APP && isAdminDetailMode()
                && store.getWorkshopId().equals(product.getWorkshopId()) ? View.VISIBLE : View.GONE);
        promotion.setOnClickListener(view -> {
            if (!BuildConfig.IS_ADMIN_APP || !isAdminDetailMode()) { return; }
            Intent intent = new Intent(this, ProductPromotionActivity.class);
            intent.putExtra("productId", product.getId());
            startActivity(intent);
        });
        duplicate.setOnClickListener(view -> {
            Intent intent = new Intent(this,ProductFormActivity.class);
            intent.putExtra("duplicateProductId",product.getId());
            startActivity(intent);
        });
        List<Product> related = new ArrayList<>();
        for (Product candidate : store.getProducts()) {
            if (candidate.getId()!=product.getId() && candidate.isActive()
                    && candidate.getCategory().equalsIgnoreCase(product.getCategory())
                    && related.size()<8) { related.add(candidate); }
        }
        RecyclerView list = findViewById(R.id.recyclerRelatedProducts);
        list.setLayoutManager(new LinearLayoutManager(this,LinearLayoutManager.HORIZONTAL,false));
        CatalogProductAdapter adapter = new CatalogProductAdapter(isAdminDetailMode(),false,
                new CatalogProductAdapter.ProductActionListener() {
                    @Override public void onOpenProduct(Product selected) { openRelatedProduct(selected); }
                    @Override public void onSecondaryAction(Product selected) { openRelatedProduct(selected); }
                });
        list.setAdapter(adapter);
        adapter.submitList(related);
        findViewById(R.id.textRelatedProductsTitle).setVisibility(related.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void openRelatedProduct(Product selected) {
        Intent intent = new Intent(this,ProductDetailActivity.class);
        intent.putExtra("productId",selected.getId());
        intent.putExtra(CatalogActivity.EXTRA_ADMIN_CATALOG,isAdminDetailMode());
        intent.putExtra(CatalogActivity.EXTRA_OPENED_FROM_ADMIN,openedFromAdmin);
        startActivity(intent);
    }

    private void shareProduct(View button) {
        if (product == null || preparingShare) {
            return;
        }
        String text = product.getName() + " · " + store.formatMoney(product.getSalePrice())
                + "\nTaller: " + product.getWorkshopName() + "\nCódigo: " + product.getInternalCode()
                + "\nWhatsApp: " + product.getWorkshopWhatsapp();
        String imageUrl = product.getImageUrl();
        if (imageUrl == null || imageUrl.trim().isEmpty()) {
            openProductShare(text, null);
            return;
        }
        preparingShare = true;
        button.setEnabled(false);
        showMessage(getString(R.string.share_product_preparing));
        shareExecutor.execute(() -> {
            Uri image = null;
            try {
                image = ProductSharing.prepareImage(getApplicationContext(), imageUrl);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception unavailableImage) {
                // Si falla la descarga, conservar el envio de texto sin bloquear al usuario.
            }
            Uri sharedImage = image;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                preparingShare = false;
                button.setEnabled(true);
                if (sharedImage == null) {
                    showMessage(getString(R.string.share_product_image_unavailable));
                }
                openProductShare(text, sharedImage);
            });
        });
    }

    private void openProductShare(String text, Uri image) {
        try {
            startActivity(Intent.createChooser(ProductSharing.createShareIntent(text, image),
                    getString(R.string.share_product_title)));
        } catch (ActivityNotFoundException unavailableApp) {
            showMessage(getString(R.string.share_product_app_unavailable));
        }
    }

    @Override
    protected void onDestroy() {
        shareExecutor.shutdownNow();
        super.onDestroy();
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
                        + (product.getInternalCode() == null
                        || product.getInternalCode()
                        .trim()
                        .isEmpty()
                        ? ""
                        : " | Código "
                        + product.getInternalCode())
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
