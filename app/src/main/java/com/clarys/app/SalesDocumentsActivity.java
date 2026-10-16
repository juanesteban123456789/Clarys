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
import com.clarys.app.data.SalesDocumentRepository;
import com.clarys.app.data.StoreCallback;
import com.clarys.app.data.SupabaseStore;
import com.clarys.app.model.OrderRequest;
import com.clarys.app.model.Sale;
import com.clarys.app.model.SalesDocument;
import com.clarys.app.util.SalesDocumentPdf;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Centro administrativo del historial local de comprobantes y cotizaciones. */
public class SalesDocumentsActivity extends BaseScreenActivity {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final List<SalesDocument> documents = new ArrayList<>();
    private SupabaseStore store;
    private String workshop;
    private EditText search;
    private Spinner type;
    private boolean loading;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        store = SupabaseStore.getInstance(this);
        if (!BuildConfig.IS_ADMIN_APP || !store.isAuthenticated()) { finish(); return; }
        workshop = store.getWorkshopId();
        setContentView(R.layout.activity_sales_documents);
        com.clarys.app.util.SalesDocumentUi.applyInsets(this);
        setupHeader(R.id.buttonHeaderHome, R.id.buttonHeaderBack);
        search = findViewById(R.id.inputDocumentSearch);
        type = findViewById(R.id.spinnerDocumentFilter);
        type.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
                getResources().getStringArray(R.array.document_filters)));
        type.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) { render(); }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) { }
        });
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence value, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence value, int start, int before, int count) { render(); }
            @Override public void afterTextChanged(Editable value) { }
        });
        findViewById(R.id.buttonNewDocument).setOnClickListener(view -> startActivity(new Intent(this, SalesDocumentFormActivity.class)));
        findViewById(R.id.buttonDocumentFromOrder).setOnClickListener(view -> chooseOrder());
        findViewById(R.id.buttonDocumentFromSale).setOnClickListener(view -> chooseSale());
    }
    private boolean active() {
        return !isFinishing() && !isDestroyed() && store.isAuthenticated()
                && workshop != null && workshop.equals(store.getWorkshopId());
    }
    @Override protected void onResume() {
        super.onResume();
        if (search == null) { return; }
        if (!active()) { finish(); return; }
        loadHistory();
    }
    private void loadHistory() {
        if (loading) { return; }
        loading = true;
        findViewById(R.id.progressDocumentHistory).setVisibility(View.VISIBLE);
        executor.execute(() -> {
            try {
                List<SalesDocument> loaded = new SalesDocumentRepository(getApplicationContext(), workshop).list();
                runOnUiThread(() -> {
                    if (!active()) { return; }
                    loading = false;
                    findViewById(R.id.progressDocumentHistory).setVisibility(View.GONE);
                    documents.clear(); documents.addAll(loaded); render();
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    if (!active()) { return; }
                    loading = false;
                    findViewById(R.id.progressDocumentHistory).setVisibility(View.GONE);
                    showMessage(getString(R.string.documents_history_error));
                });
            }
        });
    }
    private void render() {
        if (search == null) { return; }
        LinearLayout list = findViewById(R.id.containerDocumentHistory);
        list.removeAllViews();
        String query = search.getText().toString().trim().toLowerCase(new Locale("es", "CO"));
        for (SalesDocument document : documents) {
            if (type.getSelectedItemPosition() == 1 && document.getType() != SalesDocument.Type.RECEIPT
                    || type.getSelectedItemPosition() == 2 && document.getType() != SalesDocument.Type.QUOTE) { continue; }
            String searchable = document.getNumber() + " " + document.getCustomerName() + " " + document.getSourceLabel();
            if (!searchable.toLowerCase(new Locale("es", "CO")).contains(query)) { continue; }
            View row = getLayoutInflater().inflate(R.layout.item_sales_document, list, false);
            ((TextView) row.findViewById(R.id.textDocumentRowTitle)).setText(getString(R.string.documents_row_title, document.getNumber(), document.getType().label));
            ((TextView) row.findViewById(R.id.textDocumentRowCustomer)).setText(document.getCustomerName());
            ((TextView) row.findViewById(R.id.textDocumentRowDetails)).setText(getString(R.string.documents_row_details, document.getSourceLabel(),
                    new SimpleDateFormat("dd/MM/yyyy HH:mm", new Locale("es", "CO")).format(new Date(document.getCreatedAt()))));
            ((TextView) row.findViewById(R.id.textDocumentRowTotal)).setText(SalesDocumentPdf.money(document.getTotal(), document.getCurrency()));
            row.findViewById(R.id.buttonOpenSavedDocument).setOnClickListener(view -> open(document));
            row.findViewById(R.id.buttonDeleteSavedDocument).setOnClickListener(view -> confirmDelete(document));
            list.addView(row);
        }
        findViewById(R.id.textDocumentHistoryEmpty).setVisibility(list.getChildCount() == 0 ? View.VISIBLE : View.GONE);
    }
    private void open(SalesDocument document) {
        Intent intent = new Intent(this, SalesDocumentPreviewActivity.class);
        intent.putExtra("documentId", document.getId());
        startActivity(intent);
    }
    private void confirmDelete(SalesDocument document) {
        new AlertDialog.Builder(this).setTitle(R.string.documents_delete_title)
                .setMessage(R.string.documents_delete_message).setNegativeButton(R.string.documents_cancel, null)
                .setPositiveButton(R.string.documents_delete, (dialog, which) -> executor.execute(() -> {
                    try {
                        new SalesDocumentRepository(getApplicationContext(), workshop).delete(document.getId());
                        runOnUiThread(() -> { if (active()) { loadHistory(); } });
                    } catch (Exception error) {
                        runOnUiThread(() -> { if (active()) { showMessage(getString(R.string.documents_delete_error)); } });
                    }
                })).show();
    }
    private void chooseOrder() {
        findViewById(R.id.buttonDocumentFromOrder).setEnabled(false);
        store.refreshOrderRequests(new StoreCallback<List<OrderRequest>>() {
            @Override public void onSuccess(List<OrderRequest> result) {
                if (!active()) { return; }
                findViewById(R.id.buttonDocumentFromOrder).setEnabled(true);
                List<OrderRequest> available = new ArrayList<>();
                for (OrderRequest order : result) {
                    if (workshop.equals(order.getWorkshopId()) && !order.isRejected()) { available.add(order); }
                }
                if (available.isEmpty()) { showMessage(getString(R.string.documents_no_orders)); return; }
                String[] labels = new String[available.size()];
                for (int index = 0; index < labels.length; index++) {
                    OrderRequest order = available.get(index);
                    labels[index] = "#" + order.getId() + " · " + order.getCustomerName();
                }
                new AlertDialog.Builder(SalesDocumentsActivity.this).setTitle(R.string.documents_choose_order)
                        .setItems(labels, (dialog, index) -> {
                            Intent intent = new Intent(SalesDocumentsActivity.this, SalesDocumentFormActivity.class);
                            intent.putExtra("orderId", available.get(index).getId()); startActivity(intent);
                        }).show();
            }
            @Override public void onError(String message) {
                if (active()) { findViewById(R.id.buttonDocumentFromOrder).setEnabled(true); showMessage(message); }
            }
        });
    }
    private void chooseSale() {
        findViewById(R.id.buttonDocumentFromSale).setEnabled(false);
        store.refreshSales(new StoreCallback<List<Sale>>() {
            @Override public void onSuccess(List<Sale> result) {
                if (!active()) { return; }
                findViewById(R.id.buttonDocumentFromSale).setEnabled(true);
                if (result.isEmpty()) { showMessage(getString(R.string.documents_no_sales)); return; }
                String[] labels = new String[result.size()];
                for (int index = 0; index < labels.length; index++) {
                    labels[index] = "#" + result.get(index).getId() + " · " + result.get(index).getCustomerName();
                }
                new AlertDialog.Builder(SalesDocumentsActivity.this).setTitle(R.string.documents_choose_sale)
                        .setItems(labels, (dialog, index) -> {
                            Intent intent = new Intent(SalesDocumentsActivity.this, SalesDocumentFormActivity.class);
                            intent.putExtra("saleId", result.get(index).getId()); startActivity(intent);
                        }).show();
            }
            @Override public void onError(String message) {
                if (active()) { findViewById(R.id.buttonDocumentFromSale).setEnabled(true); showMessage(message); }
            }
        });
    }
    @Override protected void onDestroy() { executor.shutdownNow(); super.onDestroy(); }
}
