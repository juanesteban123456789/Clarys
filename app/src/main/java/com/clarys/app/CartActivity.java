package com.clarys.app;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.clarys.app.data.ClientPreferences;
import com.clarys.app.data.StoreCallback;
import com.clarys.app.data.SupabaseStore;
import com.clarys.app.model.CartItem;
import com.clarys.app.ui.CartAdapter;

public class CartActivity extends BaseScreenActivity {
    private SupabaseStore store;
    private CartAdapter cartAdapter;
    private TextView totalText;
    private TextView emptyText;
    private EditText nameInput;
    private EditText phoneInput;
    private ClientPreferences clientPreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cart);
        store = SupabaseStore.getInstance(this);
        clientPreferences = new ClientPreferences(this);

        setupHeader(R.id.buttonHeaderHome, R.id.buttonHeaderBack);
        bindNavigation(R.id.buttonKeepBuying, CatalogActivity.class);

        totalText = findViewById(R.id.textCartTotal);
        emptyText = findViewById(R.id.textCartEmpty);
        nameInput = findViewById(R.id.inputCustomerName);
        phoneInput = findViewById(R.id.inputCustomerPhone);
        restoreClientContact();

        RecyclerView cartList = findViewById(R.id.recyclerCartItems);
        cartList.setLayoutManager(new LinearLayoutManager(this));
        cartAdapter = new CartAdapter((CartItem item, int newQuantity) -> {
            store.updateCartQuantity(item.getProduct().getId(), newQuantity);
            refreshCart();
        });
        cartList.setAdapter(cartAdapter);

        Button confirmButton = findViewById(R.id.buttonConfirmOrder);
        confirmButton.setOnClickListener(view -> confirmOrder());
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshCart();
    }

    @Override
    protected boolean isPublicScreen() {
        return true;
    }

    private void refreshCart() {
        cartAdapter.submitList(store.getCartItems());
        totalText.setText(store.formatMoney(store.getCartTotal()));
        emptyText.setText(store.getCartItems().isEmpty()
                ? "El carrito está vacío. Agrega productos desde el catálogo."
                : "");
    }

    private void confirmOrder() {
        String customerName = nameInput.getText().toString().trim();
        String phone = ValidationUtils.digitsOnly(phoneInput.getText().toString());
        if (store.getCartItems().isEmpty()) {
            showMessage("Agrega productos antes de confirmar");
            return;
        }
        if (customerName.isEmpty()) {
            showMessage("Escribe tu nombre para el pedido");
            return;
        }
        if (!ValidationUtils.hasReasonableTextLength(customerName, ValidationUtils.MAX_SHORT_TEXT_LENGTH)) {
            showMessage("El nombre es demasiado largo");
            return;
        }
        if (!ValidationUtils.isValidPhone(phone)) {
            showMessage("Escribe un WhatsApp válido");
            return;
        }

        store.submitCatalogRequestAsync(customerName, phone, new StoreCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                clientPreferences.saveContact(
                        customerName,
                        phone
                );
                showMessage("Solicitud enviada. El taller contactará al cliente.");
                refreshCart();
                openScreen(CatalogActivity.class);
            }

            @Override
            public void onError(String message) {
                showMessage(message);
            }
        });
    }

    private void restoreClientContact() {
        nameInput.setText(
                clientPreferences.getLastName()
        );
        phoneInput.setText(
                clientPreferences.getLastPhone()
        );
    }
}
