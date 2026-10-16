package com.clarys.app;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import com.clarys.app.data.SalesDocumentCodec;
import com.clarys.app.data.SalesDocumentRepository;
import com.clarys.app.data.StoreCallback;
import com.clarys.app.data.SupabaseStore;
import com.clarys.app.model.CartItem;
import com.clarys.app.model.OrderRequest;
import com.clarys.app.model.Sale;
import com.clarys.app.model.SalesDocument;
import com.clarys.app.util.OrderStatusUtils;
import com.clarys.app.util.SalesDocumentPdf;
import org.json.JSONArray;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Editor de documentos no fiscales. Nunca confirma ventas ni escribe movimientos de inventario. */
public class SalesDocumentFormActivity extends BaseScreenActivity {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final List<SalesDocument.Line> items = new ArrayList<>();
    private SupabaseStore store;
    private String workshop, sourceKey = "", sourceLabel = "Manual", id = UUID.randomUUID().toString();
    private long createdAt = System.currentTimeMillis();
    private EditText name, phone, customerId, email, discount, notes;
    private Spinner type, payment;
    private boolean loading, saving, dirty;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        store = SupabaseStore.getInstance(this);
        if (!BuildConfig.IS_ADMIN_APP || !store.isAuthenticated()) { finish(); return; }
        workshop = store.getWorkshopId();
        try { java.util.Currency.getInstance(store.getCurrency()); }
        catch (IllegalArgumentException error) { showMessage(getString(R.string.documents_currency_error)); finish(); return; }
        getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
                | android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        setContentView(R.layout.activity_sales_document_form);
        com.clarys.app.util.SalesDocumentUi.applyInsets(this);
        setupHeader(R.id.buttonHeaderHome, R.id.buttonHeaderBack);
        name = findViewById(R.id.inputDocumentCustomer);
        phone = findViewById(R.id.inputDocumentPhone);
        customerId = findViewById(R.id.inputDocumentCustomerId);
        email = findViewById(R.id.inputDocumentEmail);
        discount = findViewById(R.id.inputDocumentDiscount);
        notes = findViewById(R.id.inputDocumentNotes);
        type = findViewById(R.id.spinnerDocumentType);
        payment = findViewById(R.id.spinnerDocumentPayment);
        type.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
                getResources().getStringArray(R.array.document_types)));
        payment.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
                getResources().getStringArray(R.array.document_payments)));
        type.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long row) {
                payment.setEnabled(position == 0);
                if (position == 1) { payment.setSelection(0); }
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) { }
        });
        findViewById(R.id.buttonAddDocumentLine).setOnClickListener(view -> editLine(-1));
        findViewById(R.id.buttonSaveDocument).setOnClickListener(view -> save());
        if (state != null) {
            try {
                id = state.getString("documentId", id); createdAt = state.getLong("createdAt", createdAt);
                sourceKey = state.getString("sourceKey", ""); sourceLabel = state.getString("sourceLabel", "Manual");
                items.addAll(SalesDocumentCodec.decodeItems(new JSONArray(state.getString("items", "[]"))));
                dirty = state.getBoolean("dirty");
                loading = state.getBoolean("loading");
                if (loading) { loadSource(); }
            } catch (Exception error) { showMessage(getString(R.string.documents_restore_error)); }
        } else { loadSource(); }
        TextWatcher changed = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { dirty = true; renderTotals(); }
            @Override public void afterTextChanged(Editable value) { }
        };
        for (EditText field : new EditText[]{name, phone, customerId, email, discount, notes}) { field.addTextChangedListener(changed); }
        renderLines();
    }
    private boolean active() {
        return !isFinishing() && !isDestroyed() && store.isAuthenticated()
                && workshop != null && workshop.equals(store.getWorkshopId());
    }
    @Override protected void onResume() {
        super.onResume();
        if (name != null && !active()) { finish(); }
    }
    private void loadSource() {
        int orderId = getIntent().getIntExtra("orderId", -1);
        int saleId = getIntent().getIntExtra("saleId", -1);
        if (orderId <= 0 && saleId <= 0) { loading = false; return; }
        loading = true;
        if (orderId > 0) {
            store.refreshOrderRequests(new StoreCallback<List<OrderRequest>>() {
                @Override public void onSuccess(List<OrderRequest> result) {
                    if (!active()) { return; }
                    OrderRequest order = store.getOrderRequest(orderId);
                    if (order == null || !workshop.equals(order.getWorkshopId()) || order.isRejected()) {
                        showMessage(getString(R.string.documents_order_unavailable)); finish(); return;
                    }
                    sourceKey = "order:" + orderId;
                    sourceLabel = "Pedido #" + orderId + " · " + OrderStatusUtils.label(order.getStatus());
                    if (order.getSaleId() > 0) {
                        loadSale(order.getSaleId(), false);
                    } else { populate(order.getCustomerName(), order.getCustomerPhone(), order.getItems(), 0); }
                }
                @Override public void onError(String message) { sourceError(message); }
            });
        } else { loadSale(saleId, true); }
    }
    private void loadSale(int saleId, boolean direct) {
        store.refreshSales(new StoreCallback<List<Sale>>() {
            @Override public void onSuccess(List<Sale> result) {
                if (!active()) { return; }
                Sale sale = store.getSale(saleId);
                if (sale == null) { sourceError(getString(R.string.documents_sale_unavailable)); return; }
                if (direct) { sourceKey = "sale:" + saleId; sourceLabel = "Venta #" + saleId; }
                else { sourceLabel += " · Venta #" + saleId; }
                populate(sale.getCustomerName(), sale.getCustomerPhone(), sale.getItems(), sale.getDiscount());
            }
            @Override public void onError(String message) { sourceError(message); }
        });
    }
    private void sourceError(String message) { if (active()) { showMessage(message); finish(); } }
    private void populate(String customer, String contact, List<CartItem> source, long sourceDiscount) {
        items.clear();
        for (CartItem item : source) {
            String description = item.getProduct().getName()
                    + (item.getVariantLabel().isEmpty() ? "" : " · " + item.getVariantLabel());
            items.add(new SalesDocument.Line(description.substring(0, Math.min(180, description.length())),
                    item.getQuantity(), item.getProduct().getSalePrice()));
        }
        name.setText(customer); phone.setText(contact); discount.setText(String.valueOf(sourceDiscount));
        loading = false; dirty = false; renderLines();
    }
    private void renderLines() {
        LinearLayout container = findViewById(R.id.containerDocumentLines);
        container.removeAllViews();
        boolean linked = !sourceKey.isEmpty();
        findViewById(R.id.buttonAddDocumentLine).setEnabled(!linked && !loading && !saving);
        discount.setEnabled(!linked && !saving && !loading);
        findViewById(R.id.buttonSaveDocument).setEnabled(!loading && !saving);
        findViewById(R.id.progressDocumentForm).setVisibility(loading || saving ? View.VISIBLE : View.GONE);
        ((TextView) findViewById(R.id.textDocumentSource)).setText(sourceLabel);
        for (int index = 0; index < items.size(); index++) {
            SalesDocument.Line item = items.get(index);
            int position = index;
            View row = getLayoutInflater().inflate(R.layout.item_document_line, container, false);
            ((TextView) row.findViewById(R.id.textDocumentLine)).setText(getString(R.string.documents_line_summary, item.getDescription(),
                    item.getQuantity(), SalesDocumentPdf.money(item.getUnitPrice(), store.getCurrency()),
                    SalesDocumentPdf.money(item.getSubtotal(), store.getCurrency())));
            row.findViewById(R.id.buttonEditDocumentLine).setVisibility(linked ? View.GONE : View.VISIBLE);
            row.findViewById(R.id.buttonRemoveDocumentLine).setVisibility(linked ? View.GONE : View.VISIBLE);
            row.findViewById(R.id.buttonEditDocumentLine).setOnClickListener(view -> editLine(position));
            row.findViewById(R.id.buttonRemoveDocumentLine).setOnClickListener(view -> { items.remove(position); dirty = true; renderLines(); });
            container.addView(row);
        }
        renderTotals();
    }
    private void renderTotals() {
        long subtotal = 0;
        for (SalesDocument.Line item : items) { subtotal += item.getSubtotal(); }
        long reduction = 0;
        try { reduction = Long.parseLong(discount.getText().toString()); } catch (NumberFormatException ignored) { }
        ((TextView) findViewById(R.id.textDocumentTotals)).setText(getString(R.string.documents_totals,
                SalesDocumentPdf.money(subtotal, store.getCurrency()),
                SalesDocumentPdf.money(Math.max(0, subtotal - reduction), store.getCurrency())));
    }
    private void editLine(int position) {
        if (saving || loading) { return; }
        if (position < 0 && items.size() >= SalesDocument.MAX_ITEMS) {
            showMessage(getString(R.string.documents_max_lines)); return;
        }
        View view = getLayoutInflater().inflate(R.layout.dialog_document_line, null);
        EditText description = view.findViewById(R.id.inputDocumentLineDescription);
        EditText quantity = view.findViewById(R.id.inputDocumentLineQuantity);
        EditText price = view.findViewById(R.id.inputDocumentLinePrice);
        quantity.setText("1");
        if (position >= 0) {
            SalesDocument.Line line = items.get(position);
            description.setText(line.getDescription()); quantity.setText(String.valueOf(line.getQuantity()));
            price.setText(String.valueOf(line.getUnitPrice()));
        }
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(R.string.documents_line_title).setView(view)
                .setNegativeButton(R.string.documents_cancel, null).setPositiveButton(R.string.documents_accept, null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(button -> {
            if (description.getText().toString().trim().isEmpty()) { description.setError(getString(R.string.documents_description_required)); return; }
            int count;
            long amount;
            try { count = Integer.parseInt(quantity.getText().toString()); if (count < 1 || count > 100000) { throw new NumberFormatException(); } }
            catch (NumberFormatException error) { quantity.setError(getString(R.string.documents_quantity_error)); return; }
            try { amount = Long.parseLong(price.getText().toString()); if (amount < 0 || amount > 100000000L) { throw new NumberFormatException(); } }
            catch (NumberFormatException error) { price.setError(getString(R.string.documents_price_error)); return; }
            SalesDocument.Line line;
            try { line = new SalesDocument.Line(description.getText().toString(), count, amount); }
            catch (IllegalArgumentException error) { description.setError(error.getMessage()); return; }
            if (position < 0) { items.add(line); } else { items.set(position, line); }
            dirty = true; renderLines(); dialog.dismiss();
        }));
        dialog.show();
    }
    private void save() {
        if (!active() || saving || loading) { return; }
        if (name.getText().toString().trim().isEmpty()) { name.setError(getString(R.string.documents_customer_required)); name.requestFocus(); return; }
        if (!phone.getText().toString().trim().isEmpty() && !ValidationUtils.isValidPhone(phone.getText().toString())) {
            phone.setError(getString(R.string.documents_phone_error)); return;
        }
        if (!email.getText().toString().trim().isEmpty()
                && !android.util.Patterns.EMAIL_ADDRESS.matcher(email.getText().toString().trim()).matches()) {
            email.setError(getString(R.string.documents_email_error)); return;
        }
        if (items.isEmpty()) { showMessage(getString(R.string.documents_items_required)); return; }
        long reduction;
        try { reduction = discount.getText().toString().isEmpty() ? 0 : Long.parseLong(discount.getText().toString()); }
        catch (NumberFormatException error) { discount.setError(getString(R.string.documents_discount_error)); return; }
        long subtotal = 0;
        for (SalesDocument.Line item : items) { subtotal += item.getSubtotal(); }
        if (reduction < 0 || reduction > subtotal) { discount.setError(getString(R.string.documents_discount_error)); return; }
        SalesDocument document;
        try {
            document = new SalesDocument.Builder(workshop, store.getBusinessName(), store.getContactWhatsapp(), store.getCurrency())
                    .identity(id, createdAt).customer(name.getText().toString(), phone.getText().toString(),
                            customerId.getText().toString(), email.getText().toString())
                    .type(type.getSelectedItemPosition() == 0 ? SalesDocument.Type.RECEIPT : SalesDocument.Type.QUOTE)
                    .payment(payment.getSelectedItemPosition() == 0 ? SalesDocument.Payment.UNVERIFIED : SalesDocument.Payment.RECORDED)
                    .source(sourceKey, sourceLabel).notes(notes.getText().toString()).discount(reduction).items(items).build();
        } catch (IllegalArgumentException error) { showMessage(error.getMessage()); return; }
        saving = true; renderLines();
        executor.execute(() -> {
            try {
                SalesDocument stored = new SalesDocumentRepository(getApplicationContext(), workshop).save(document,
                        (value, output) -> SalesDocumentPdf.write(getApplicationContext(), value, output));
                runOnUiThread(() -> {
                    if (!active()) { return; }
                    if (!stored.getId().equals(document.getId())) { showMessage(getString(R.string.documents_existing)); }
                    dirty = false; saving = false;
                    Intent intent = new Intent(this, SalesDocumentPreviewActivity.class);
                    intent.putExtra("documentId", stored.getId()); startActivity(intent); finish();
                });
            } catch (Exception error) {
                runOnUiThread(() -> { if (active()) { saving = false; renderLines(); showMessage(getString(R.string.documents_save_error)); } });
            }
        });
    }
    @Override protected void requestLeaveScreen(Runnable exit) {
        if (saving) { showMessage(getString(R.string.documents_wait)); return; }
        if (!dirty) { exit.run(); return; }
        new AlertDialog.Builder(this).setTitle(R.string.documents_discard_title).setMessage(R.string.documents_discard_message)
                .setNegativeButton(R.string.documents_cancel, null).setPositiveButton(R.string.documents_discard, (dialog, which) -> exit.run()).show();
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        state.putString("documentId", id); state.putLong("createdAt", createdAt);
        state.putString("sourceKey", sourceKey); state.putString("sourceLabel", sourceLabel);
        state.putBoolean("dirty", dirty); state.putBoolean("loading", loading);
        try { state.putString("items", SalesDocumentCodec.encodeItems(items).toString()); } catch (org.json.JSONException ignored) { }
        super.onSaveInstanceState(state);
    }
    @Override protected void onDestroy() { executor.shutdownNow(); super.onDestroy(); }
}
