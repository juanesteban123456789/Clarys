package com.clarys.app;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.clarys.app.data.StoreCallback;
import com.clarys.app.data.SupabaseStore;
import com.clarys.app.model.CartItem;
import com.clarys.app.model.Product;
import com.clarys.app.model.Sale;
import com.clarys.app.ui.CartAdapter;
import com.clarys.app.ui.ProductAdapter;

public class SaleActivity extends BaseScreenActivity {
    private SupabaseStore store;
    private ProductAdapter productAdapter;
    private CartAdapter cartAdapter;
    private EditText searchInput;
    private EditText customerInput;
    private EditText phoneInput;
    private EditText discountInput;
    private Spinner paymentSpinner;
    private TextView cartTotalText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sale);
        store = SupabaseStore.getInstance(this);

        setupHeader(R.id.buttonHeaderHome, R.id.buttonHeaderBack);
        searchInput = findViewById(R.id.inputSaleSearch);
        customerInput = findViewById(R.id.inputSaleCustomer);
        phoneInput = findViewById(R.id.inputSalePhone);
        discountInput = findViewById(R.id.inputSaleDiscount);
        paymentSpinner = findViewById(R.id.spinnerPaymentMethod);
        cartTotalText = findViewById(R.id.textSaleCartTotal);

        paymentSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
                new String[]{"Efectivo", "Transferencia", "Nequi", "Pendiente"}));

        RecyclerView productsList = findViewById(R.id.recyclerSaleProducts);
        productsList.setLayoutManager(new LinearLayoutManager(this));
        productAdapter = new ProductAdapter("Agregar", "Detalle", true, new ProductAdapter.ProductActionListener() {
            @Override
            public void onPrimaryAction(Product product) {
                if (store.addToCart(product.getId(), 1)) {
                    showMessage("Agregado a venta");
                    refreshCart();
                } else {
                    showMessage("Sin stock disponible");
                }
            }

            @Override
            public void onSecondaryAction(Product product) {
                Intent intent = new Intent(SaleActivity.this, ProductDetailActivity.class);
                intent.putExtra("productId", product.getId());
                startActivity(intent);
            }
        });
        productsList.setAdapter(productAdapter);

        RecyclerView cartList = findViewById(R.id.recyclerSaleCart);
        cartList.setLayoutManager(new LinearLayoutManager(this));
        cartAdapter = new CartAdapter((CartItem item, int newQuantity) -> {
            store.updateCartQuantity(item.getProduct().getId(), newQuantity);
            refreshCart();
        });
        cartList.setAdapter(cartAdapter);

        findViewById(R.id.buttonSaleSearch).setOnClickListener(view -> refreshProducts());
        findViewById(R.id.buttonClearSaleCart).setOnClickListener(view -> {
            store.clearCart();
            refreshCart();
        });
        findViewById(R.id.buttonConfirmSale).setOnClickListener(view -> confirmSale());
    }

    @Override
    protected void onResume() {
        super.onResume();
        store.refreshProducts(true, new StoreCallback<java.util.List<Product>>() {
            @Override
            public void onSuccess(java.util.List<Product> result) {
                refreshProducts();
            }

            @Override
            public void onError(String message) {
                showMessage(message);
                refreshProducts();
            }
        });
        refreshCart();
    }

    private void refreshProducts() {
        productAdapter.submitList(store.searchProducts(searchInput.getText().toString(), "Todos"));
    }

    private void refreshCart() {
        cartAdapter.submitList(store.getCartItems());
        cartTotalText.setText(store.formatMoney(store.getCartTotal()));
    }

    private void confirmSale() {
        store.confirmSaleAsync(customerInput.getText().toString(), phoneInput.getText().toString(),
                paymentSpinner.getSelectedItem().toString(), parseDiscount(), "Confirmada",
                new StoreCallback<Sale>() {
                    @Override
                    public void onSuccess(Sale sale) {
                        showMessage("Venta registrada");
                        Intent intent = new Intent(SaleActivity.this, SaleDetailActivity.class);
                        if (sale != null) {
                            intent.putExtra("saleId", sale.getId());
                        }
                        startActivity(intent);
                    }

                    @Override
                    public void onError(String message) {
                        showMessage(message);
                    }
                });
    }

    private int parseDiscount() {
        try {
            return Integer.parseInt(discountInput.getText().toString().replace("$", "").replace(".", "").trim());
        } catch (NumberFormatException exception) {
            return 0;
        }
    }
}
