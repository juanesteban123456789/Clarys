package com.clarys.app;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import java.util.List;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.clarys.app.data.ClientPreferences;
import com.clarys.app.data.StoreCallback;
import com.clarys.app.data.SupabaseStore;
import com.clarys.app.model.CartItem;
import com.clarys.app.ui.CartAdapter;

public class CartActivity extends BaseScreenActivity {
    @Override protected void requestLeaveScreen(Runnable navigation) {
        if (sending) { showMessage(getString(R.string.navigation_wait_send)); return; }
        navigation.run();
    }
    private SupabaseStore store;
    private CartAdapter cartAdapter;
    private TextView totalText;
    private TextView emptyText;
    private EditText nameInput;
    private EditText phoneInput;
    private ClientPreferences clientPreferences;
    private boolean openedFromAdmin;
    private boolean sending;
    private Button confirmButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cart);
        store = SupabaseStore.getInstance(this);
        clientPreferences = new ClientPreferences(this);
        openedFromAdmin = BuildConfig.IS_ADMIN_APP
                && getIntent().getBooleanExtra(CatalogActivity.EXTRA_OPENED_FROM_ADMIN, false)
                && AppModeManager.isAdminMode(this)
                && store.isAuthenticated();

        setupHeader(R.id.buttonHeaderHome, R.id.buttonHeaderBack);
        findViewById(R.id.buttonKeepBuying).setOnClickListener(view -> openCatalog());

        totalText = findViewById(R.id.textCartTotal);
        emptyText = findViewById(R.id.textCartEmpty);
        nameInput = findViewById(R.id.inputCustomerName);
        phoneInput = findViewById(R.id.inputCustomerPhone);
        restoreClientContact();

        RecyclerView cartList = findViewById(R.id.recyclerCartItems);
        cartList.setLayoutManager(new LinearLayoutManager(this));
        cartAdapter = new CartAdapter((CartItem item, int newQuantity) -> {
            if (sending) { return; }
            store.updateCartQuantity(item.getProduct().getId(), item.getVariantId(), newQuantity);
            refreshCart();
        });
        cartList.setAdapter(cartAdapter);

        confirmButton = findViewById(R.id.buttonConfirmOrder);
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
        if (sending) { return; }
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

        setSending(true);
        store.revalidateCart(new StoreCallback<List<String>>() {
            @Override public void onSuccess(List<String> changes) {
                setSending(false);
                refreshCart();
                if (!changes.isEmpty()) {
                    new AlertDialog.Builder(CartActivity.this).setTitle("El carrito cambió")
                            .setMessage(String.join("\n", changes)+"\n\nRevisa el total antes de enviar.")
                            .setPositiveButton("Revisar",null).show();
                } else {
                    reviewOrder(customerName,phone);
                }
            }
            @Override public void onError(String message) { setSending(false); showMessage(message); }
        });
    }

    private void reviewOrder(String name, String phone) {
        StringBuilder summary = new StringBuilder();
        for (CartItem item : store.getCartItems()) {
            summary.append(item.getQuantity()).append(" × ").append(item.getProduct().getName());
            if (!item.getVariantLabel().isEmpty()) { summary.append(" (").append(item.getVariantLabel()).append(")"); }
            summary.append(" · ").append(store.formatMoney(item.getSubtotal())).append("\n");
        }
        String workshop = store.getCartItems().get(0).getProduct().getWorkshopName();
        summary.append("\nTaller: ").append(workshop).append("\nTotal: ")
                .append(store.formatMoney(store.getCartTotal())).append("\nContacto: ").append(name).append(" · ").append(phone);
        new AlertDialog.Builder(this).setTitle("Revisar pedido").setMessage(summary.toString())
                .setNegativeButton("Seguir editando",null)
                .setPositiveButton("Enviar solicitud",(dialog,which)->sendOrder(name,phone)).show();
    }

    private void setSending(boolean value) {
        sending = value;
        confirmButton.setEnabled(!value);
        nameInput.setEnabled(!value);
        phoneInput.setEnabled(!value);
        findViewById(R.id.buttonKeepBuying).setEnabled(!value);
        confirmButton.setText(value ? "Validando pedido..." : "Revisar y enviar pedido");
    }

    private void sendOrder(String customerName, String phone) {
        if (sending) { return; }
        setSending(true);
        store.submitCatalogRequestAsync(customerName, phone, new StoreCallback<String>() {
            @Override
            public void onSuccess(String trackingToken) {
                setSending(false);
                clientPreferences.saveContact(
                        customerName,
                        phone
                );
                clientPreferences.saveTrackingToken(trackingToken);
                refreshCart();
                new AlertDialog.Builder(CartActivity.this).setTitle("Pedido enviado")
                        .setMessage("Tu solicitud quedó registrada. Puedes consultar su estado en Mis pedidos.")
                        .setPositiveButton("Ver mis pedidos",(dialog,which)-> {
                            Intent intent = new Intent(CartActivity.this,ClientOrdersActivity.class);
                            intent.putExtra(CatalogActivity.EXTRA_OPENED_FROM_ADMIN,openedFromAdmin);
                            intent.putExtra("autoSearch",true);
                            startActivity(intent);
                        }).setNegativeButton("Volver al catálogo",(dialog,which)->openCatalog()).show();
            }

            @Override
            public void onError(String message) {
                setSending(false);
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

    private void openCatalog() {
        if (!BuildConfig.IS_ADMIN_APP) { navigateHome(); return; }
        Intent intent = new Intent(this, CatalogActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        intent.putExtra(CatalogActivity.EXTRA_ADMIN_CATALOG, false);
        intent.putExtra(CatalogActivity.EXTRA_OPENED_FROM_ADMIN, openedFromAdmin);
        startActivity(intent);
    }
}
