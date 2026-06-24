package com.clarys.app;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.clarys.app.data.MockStore;
import com.clarys.app.model.CartItem;
import com.clarys.app.model.Sale;
import com.clarys.app.ui.CartAdapter;

public class CartActivity extends BaseScreenActivity {
    private final MockStore store = MockStore.getInstance();
    private CartAdapter cartAdapter;
    private TextView totalText;
    private TextView emptyText;
    private EditText nameInput;
    private EditText phoneInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cart);

        setupHeader(R.id.buttonHeaderHome, R.id.buttonHeaderBack);
        bindNavigation(R.id.buttonKeepBuying, CatalogActivity.class);

        totalText = findViewById(R.id.textCartTotal);
        emptyText = findViewById(R.id.textCartEmpty);
        nameInput = findViewById(R.id.inputCustomerName);
        phoneInput = findViewById(R.id.inputCustomerPhone);

        RecyclerView cartList = findViewById(R.id.recyclerCartItems);
        cartList.setLayoutManager(new LinearLayoutManager(this));
        cartAdapter = new CartAdapter((CartItem item, int newQuantity) -> {
            store.updateCartQuantity(item.getProduct().getId(), newQuantity);
            refreshCart();
        });
        cartList.setAdapter(cartAdapter);

        Button confirmButton = findViewById(R.id.buttonConfirmOrder);
        confirmButton.setOnClickListener(view -> {
            Sale sale = store.confirmSale(nameInput.getText().toString(), phoneInput.getText().toString(),
                    "Pendiente por WhatsApp", 0, "Pedido pendiente");
            if (sale == null) {
                showMessage("Agrega productos antes de confirmar");
                return;
            }
            showMessage("Pedido confirmado");
            Intent intent = new Intent(this, SaleDetailActivity.class);
            intent.putExtra("saleId", sale.getId());
            startActivity(intent);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshCart();
    }

    private void refreshCart() {
        cartAdapter.submitList(store.getCartItems());
        totalText.setText(store.formatMoney(store.getCartTotal()));
        emptyText.setText(store.getCartItems().isEmpty()
                ? "El carrito esta vacio. Agrega productos desde catalogo o nueva venta."
                : "");
    }
}
