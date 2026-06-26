package com.clarys.app;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.clarys.app.data.SupabaseStore;
import com.clarys.app.model.Product;

public class ProductDetailActivity extends BaseScreenActivity {
    private SupabaseStore store;
    private Product product;
    private boolean adminCatalog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_product_detail);
        store = SupabaseStore.getInstance(this);
        adminCatalog = getIntent().getBooleanExtra("adminCatalog", false);

        setupHeader(R.id.buttonHeaderHome, R.id.buttonHeaderBack);

        Button addToCartButton = findViewById(R.id.buttonAddToCart);
        Button backToCatalogButton = findViewById(R.id.buttonBackToCatalog);
        Button inventoryButton = findViewById(R.id.buttonSeeInventory);
        Button editButton = findViewById(R.id.buttonEditProduct);
        boolean adminMode = store.isAuthenticated();
        int adminVisibility = adminMode ? View.VISIBLE : View.GONE;
        inventoryButton.setVisibility(adminVisibility);
        editButton.setVisibility(adminVisibility);
        addToCartButton.setVisibility(adminMode ? View.GONE : View.VISIBLE);

        addToCartButton.setOnClickListener(view -> {
            if (product != null && store.addToCart(product.getId(), 1)) {
                showMessage("Producto agregado al carrito");
                openScreen(CartActivity.class);
            } else {
                showMessage("Producto sin stock disponible");
            }
        });

        backToCatalogButton.setOnClickListener(view -> {
            Intent intent = new Intent(this, CatalogActivity.class);
            intent.putExtra("adminCatalog", adminCatalog);
            startActivity(intent);
        });
        inventoryButton.setOnClickListener(view -> {
            if (product != null) {
                Intent intent = new Intent(this, InventoryMovementActivity.class);
                intent.putExtra("productId", product.getId());
                startActivity(intent);
            }
        });
        editButton.setOnClickListener(view -> {
            if (product != null) {
                Intent intent = new Intent(this, ProductFormActivity.class);
                intent.putExtra("productId", product.getId());
                startActivity(intent);
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        int productId = getIntent().getIntExtra("productId", 1);
        product = store.getProduct(productId);
        if (product == null && !store.getProducts().isEmpty()) {
            product = store.getProducts().get(0);
        }
        renderProduct();
    }

    @Override
    protected boolean isPublicScreen() {
        return true;
    }

    private void renderProduct() {
        if (product == null) {
            showMessage("Producto no encontrado");
            return;
        }

        ((TextView) findViewById(R.id.textDetailName)).setText(product.getName());
        ((TextView) findViewById(R.id.textDetailPrice)).setText(store.formatMoney(product.getSalePrice()));
        ((TextView) findViewById(R.id.textDetailDescription)).setText(product.getDescription());
        ((TextView) findViewById(R.id.textDetailCategory)).setText(product.getCategory() + " | SKU " + product.getSku());
        ((TextView) findViewById(R.id.textDetailSizes)).setText(product.getSizes());
        ((TextView) findViewById(R.id.textDetailColors)).setText(product.getColors());
        ((TextView) findViewById(R.id.textDetailStock)).setText("Stock " + product.getStock()
                + " | minimo " + product.getMinStock());
        ImageView image = findViewById(R.id.imageDetailProduct);
        if (product.getImageUrl().isEmpty()) {
            image.setImageResource(android.R.color.transparent);
        } else {
            Glide.with(this)
                    .load(product.getImageUrl())
                    .centerCrop()
                    .into(image);
        }
    }
}
