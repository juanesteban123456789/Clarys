package com.clarys.app;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RadioGroup;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import com.clarys.app.data.SupabaseStore;
import com.clarys.app.model.Product;
import com.clarys.app.model.ProductPromotion;
import com.clarys.app.util.ProductPromotionImage;
import com.clarys.app.util.ProductSharing;
import com.clarys.app.util.SalesDocumentUi;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Prepara contenido local para revision y publicacion manual. No usa la API ni claves de Instagram. */
public class ProductPromotionActivity extends BaseScreenActivity {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private SupabaseStore store;
    private String workshop;
    private Product product;
    private ProductPromotion promotion;
    private CheckBox price, contact;
    private RadioGroup format;
    private EditText caption;
    private Bitmap photo;
    private Uri image, pendingExport;
    private boolean working, dirty, updatingCaption;
    private final ActivityResultLauncher<String> saveImage = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("image/jpeg"), uri -> {
                Uri source = pendingExport;
                pendingExport = null;
                if (uri != null && source != null && active()) { export(source, uri); }
            });

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        store = SupabaseStore.getInstance(this);
        if (!BuildConfig.IS_ADMIN_APP || !store.isAuthenticated() || !AppModeManager.isAdminMode(this)) { finish(); return; }
        workshop = store.getWorkshopId();
        product = store.getProduct(getIntent().getIntExtra("productId", -1));
        if (workshop == null || workshop.isEmpty() || product == null
                || !workshop.equals(product.getWorkshopId()) || !product.isActive()) {
            showMessage(getString(R.string.promotion_unavailable)); finish(); return;
        }
        if (product.getImageUrl().isEmpty()) {
            showMessage(getString(R.string.promotion_missing_photo)); finish(); return;
        }
        promotion = new ProductPromotion(product, store.getBusinessName(),
                store.formatMoney(product.getSalePrice()), store.getContactWhatsapp());
        setContentView(R.layout.activity_product_promotion);
        getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                | android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        SalesDocumentUi.applyInsets(this);
        setupHeader(R.id.buttonHeaderHome, R.id.buttonHeaderBack);
        caption = findViewById(R.id.inputPromotionCaption);
        price = findViewById(R.id.checkPromotionPrice);
        contact = findViewById(R.id.checkPromotionContact);
        format = findViewById(R.id.groupPromotionFormat);
        contact.setVisibility(promotion.getPhone().isEmpty() ? View.GONE : View.VISIBLE);
        if (state != null && workshop.equals(state.getString("workshop"))) {
            price.setChecked(state.getBoolean("price", true));
            contact.setChecked(state.getBoolean("contact", true));
            format.check(state.getBoolean("story") ? R.id.radioPromotionStory : R.id.radioPromotionPost);
            caption.setText(state.getString("caption", ""));
            dirty = state.getBoolean("dirty");
            String pending = state.getString("pendingExport");
            if (pending != null) {
                Uri candidate = Uri.parse(pending);
                if ("content".equals(candidate.getScheme()) && (getPackageName() + ".productsharing").equals(candidate.getAuthority())) {
                    pendingExport = candidate;
                }
            }
        } else { caption.setText(promotion.caption(price.isChecked(), contact.isChecked())); }
        caption.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { if (!updatingCaption) { dirty = true; } }
            @Override public void afterTextChanged(Editable value) { }
        });
        price.setOnCheckedChangeListener((button, checked) -> optionsChanged());
        contact.setOnCheckedChangeListener((button, checked) -> optionsChanged());
        format.setOnCheckedChangeListener((group, checked) -> render());
        findViewById(R.id.buttonPromotionRetry).setOnClickListener(view -> render());
        findViewById(R.id.buttonPromotionCopy).setOnClickListener(view -> {
            if (caption.getText().toString().trim().isEmpty()) { caption.setError(getString(R.string.promotion_empty)); return; }
            getSystemService(ClipboardManager.class).setPrimaryClip(ClipData.newPlainText(
                    getString(R.string.promotion_caption), caption.getText().toString()));
            showMessage(getString(R.string.promotion_copied));
        });
        findViewById(R.id.buttonPromotionReset).setOnClickListener(view -> new AlertDialog.Builder(this)
                .setTitle(R.string.promotion_reset_title).setMessage(R.string.promotion_reset_message)
                .setNegativeButton(R.string.documents_cancel, null)
                .setPositiveButton(R.string.documents_accept, (dialog, which) ->
                        caption.setText(promotion.caption(price.isChecked(), contact.isChecked()))).show());
        findViewById(R.id.buttonPromotionSave).setOnClickListener(view -> {
            if (!active() || working || image == null) { return; }
            pendingExport = image;
            saveImage.launch("Clarys_" + product.getId() + "_" + (isStory() ? "historia" : "publicacion") + ".jpg");
        });
        findViewById(R.id.buttonPromotionShare).setOnClickListener(view -> {
            if (!active() || working || image == null) { return; }
            try {
                startActivity(Intent.createChooser(ProductSharing.createShareIntent(caption.getText().toString(), image),
                        getString(R.string.promotion_share)));
            } catch (android.content.ActivityNotFoundException error) { showMessage(getString(R.string.promotion_export_error)); }
        });
        render();
    }

    private boolean active() {
        return store != null && !isFinishing() && !isDestroyed() && store.isAuthenticated()
                && workshop != null && workshop.equals(store.getWorkshopId()) && AppModeManager.isAdminMode(this);
    }
    @Override protected void onResume() { super.onResume(); if (store != null && !active()) { finish(); } }
    private boolean isStory() { return format.getCheckedRadioButtonId() == R.id.radioPromotionStory; }

    private void optionsChanged() {
        // Conservar ediciones propias; solo regenerar automaticamente el texto no editado.
        if (!dirty) {
            updatingCaption = true;
            caption.setText(promotion.caption(price.isChecked(), contact.isChecked()));
            updatingCaption = false;
        }
        render();
    }

    private void render() {
        if (!active() || working) { return; }
        working = true;
        image = null;
        controls();
        findViewById(R.id.buttonPromotionRetry).setVisibility(View.GONE);
        ProductPromotion.Format selected = isStory() ? ProductPromotion.Format.STORY : ProductPromotion.Format.POST;
        boolean includePrice = price.isChecked(), includeContact = contact.isChecked();
        executor.execute(() -> {
            Bitmap prepared = null;
            try {
                if (photo == null) {
                    Uri original = ProductSharing.prepareImage(getApplicationContext(), product.getImageUrl());
                    try (InputStream input = getContentResolver().openInputStream(original)) {
                        photo = BitmapFactory.decodeStream(input);
                    }
                    if (photo == null) { throw new IOException("Imagen no disponible"); }
                }
                prepared = ProductPromotionImage.render(getApplicationContext(), promotion, photo, selected, includePrice, includeContact);
                Uri ready = ProductSharing.prepareBitmap(getApplicationContext(), prepared);
                Bitmap preview = prepared;
                runOnUiThread(() -> {
                    if (!active()) { preview.recycle(); return; }
                    image = ready; working = false;
                    ((ImageView) findViewById(R.id.imagePromotionPreview)).setImageBitmap(preview);
                    controls();
                });
            } catch (Exception error) {
                if (prepared != null) { prepared.recycle(); }
                runOnUiThread(() -> {
                    if (!active()) { return; }
                    working = false; controls();
                    findViewById(R.id.buttonPromotionRetry).setVisibility(View.VISIBLE);
                    showMessage(getString(R.string.promotion_error));
                });
            }
        });
    }

    private void controls() {
        price.setEnabled(!working); contact.setEnabled(!working);
        for (int i = 0; i < format.getChildCount(); i++) { format.getChildAt(i).setEnabled(!working); }
        findViewById(R.id.buttonPromotionSave).setEnabled(!working && image != null);
        findViewById(R.id.buttonPromotionShare).setEnabled(!working && image != null);
        findViewById(R.id.progressPromotion).setVisibility(working ? View.VISIBLE : View.GONE);
    }
    private void export(Uri source, Uri destination) {
        working = true; controls();
        executor.execute(() -> {
            try {
                try (InputStream input = getContentResolver().openInputStream(source);
                     OutputStream output = getContentResolver().openOutputStream(destination, "w")) {
                    if (input == null || output == null) { throw new IOException("Destino no disponible"); }
                    byte[] buffer = new byte[8192]; int count;
                    while ((count = input.read(buffer)) != -1) { output.write(buffer, 0, count); }
                }
                runOnUiThread(() -> { if (active()) { working = false; controls(); showMessage(getString(R.string.promotion_saved)); } });
            } catch (Exception error) {
                runOnUiThread(() -> { if (active()) { working = false; controls(); showMessage(getString(R.string.promotion_export_error)); } });
            }
        });
    }
    @Override protected void requestLeaveScreen(Runnable action) {
        if (!dirty) { action.run(); return; }
        new AlertDialog.Builder(this).setTitle(R.string.promotion_discard_title).setMessage(R.string.promotion_discard_message)
                .setNegativeButton(R.string.documents_cancel, null)
                .setPositiveButton(R.string.documents_discard, (dialog, which) -> action.run()).show();
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        if (caption != null) {
            state.putString("workshop", workshop); state.putString("caption", caption.getText().toString());
            state.putBoolean("price", price.isChecked()); state.putBoolean("contact", contact.isChecked());
            state.putBoolean("story", isStory()); state.putBoolean("dirty", dirty);
            if (pendingExport != null) { state.putString("pendingExport", pendingExport.toString()); }
        }
        super.onSaveInstanceState(state);
    }
    @Override protected void onDestroy() { executor.shutdownNow(); super.onDestroy(); }
}
