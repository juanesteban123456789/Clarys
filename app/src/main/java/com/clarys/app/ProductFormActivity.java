package com.clarys.app;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.text.Editable;
import android.text.TextWatcher;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;

import com.bumptech.glide.Glide;
import com.clarys.app.data.StoreCallback;
import com.clarys.app.data.SupabaseStore;
import com.clarys.app.model.Product;
import com.clarys.app.model.ProductVariant;
import com.clarys.app.ui.VariantEditor;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.text.Normalizer;
import java.util.Locale;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;

public class ProductFormActivity extends BaseScreenActivity {
    private static final int MAX_IMAGE_SIZE = 900;
    private static final int IMAGE_QUALITY = 70;

    private SupabaseStore store;
    private Integer productId;
    private ActivityResultLauncher<String> imagePicker;
    private Uri selectedImageUri;
    private String productImageUrl = "";
    private EditText nameInput;
    private EditText descriptionInput;
    private EditText categoryInput;
    private EditText purchasePriceInput;
    private EditText salePriceInput;
    private EditText stockInput;
    private EditText minStockInput;
    private EditText sizesInput;
    private EditText colorsInput;
    private EditText internalCodeInput;
    private CheckBox activeInput;
    private Button imageButton;
    private Button saveButton;
    private Button cancelButton;
    private Button advancedFieldsButton;
    private ImageView imagePreview;
    private View saveStatusContainer;
    private TextView saveStatusText;
    private View internalCodeSection;
    private View advancedFieldsSection;
    private boolean advancedFieldsVisible;
    private boolean formInitialized;
    private boolean saving;
    private String initialFormState = "";
    private List<ProductVariant> variants = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_product_form);
        store = SupabaseStore.getInstance(this);
        imagePicker = registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
            if (uri == null) {
                return;
            }
            selectedImageUri = uri;
            imageButton.setText("Imagen seleccionada");
            imagePreview.setVisibility(View.VISIBLE);
            imagePreview.setImageURI(uri);
            showMessage("Imagen lista para subir al guardar");
        });

        setupHeader(R.id.buttonHeaderHome, R.id.buttonHeaderBack);
        nameInput = findViewById(R.id.inputProductName);
        descriptionInput = findViewById(R.id.inputProductDescription);
        categoryInput = findViewById(R.id.inputProductCategory);
        Set<String> categories = new LinkedHashSet<>();
        for (Product product : store.getProducts()) { categories.add(product.getCategory()); }
        ((AutoCompleteTextView) categoryInput).setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_dropdown_item_1line, new ArrayList<>(categories)));
        ((AutoCompleteTextView) categoryInput).setThreshold(0);
        categoryInput.setOnClickListener(view -> ((AutoCompleteTextView) categoryInput).showDropDown());
        purchasePriceInput = findViewById(R.id.inputProductPurchasePrice);
        salePriceInput = findViewById(R.id.inputProductSalePrice);
        stockInput = findViewById(R.id.inputProductStock);
        minStockInput = findViewById(R.id.inputProductMinStock);
        sizesInput = findViewById(R.id.inputProductSizes);
        colorsInput = findViewById(R.id.inputProductColors);
        internalCodeInput = findViewById(R.id.inputProductInternalCode);
        activeInput = findViewById(R.id.checkProductActive);
        imagePreview = findViewById(R.id.imageProductPreview);
        advancedFieldsButton = findViewById(R.id.buttonToggleProductAdvanced);
        saveButton = findViewById(R.id.buttonSaveProductForm);
        cancelButton = findViewById(R.id.buttonCancelProductForm);
        saveStatusContainer = findViewById(R.id.layoutProductSaveStatus);
        saveStatusText = findViewById(R.id.textProductSaveStatus);
        internalCodeSection = findViewById(R.id.layoutProductInternalCode);
        advancedFieldsSection = findViewById(R.id.layoutProductAdvancedFields);
        advancedFieldsButton.setOnClickListener(view -> toggleAdvancedFields());
        renderAdvancedFields();

        int extraProductId = getIntent().getIntExtra("productId", -1);
        productId = extraProductId == -1 ? null : extraProductId;

        imageButton = findViewById(R.id.buttonProductImage);
        imageButton.setOnClickListener(view -> imagePicker.launch("image/*"));
        saveButton.setOnClickListener(view -> saveProduct());
        cancelButton.setOnClickListener(view -> navigateHome());
        renderProduct();
        setupProductTools();
    }

    private void renderProduct() {
        int duplicateId = getIntent().getIntExtra("duplicateProductId", -1);
        if (productId == null && duplicateId < 0) {
            minStockInput.setText(String.valueOf(store.getDefaultMinStock()));
            activeInput.setChecked(true);
            markInitialFormState();
            return;
        }

        Product product = store.getProduct(productId == null ? duplicateId : productId);
        if (product == null) {
            showMessage("Producto no encontrado");
            finish();
            return;
        }

        nameInput.setText(product.getName());
        descriptionInput.setText(product.getDescription());
        categoryInput.setText(product.getCategory());
        purchasePriceInput.setText(String.valueOf(product.getPurchasePrice()));
        salePriceInput.setText(String.valueOf(product.getSalePrice()));
        stockInput.setText(String.valueOf(product.getTotalStock()));
        variants = product.getVariants();
        minStockInput.setText(String.valueOf(product.getMinStock()));
        sizesInput.setText(product.getSizes());
        colorsInput.setText(product.getColors());
        internalCodeInput.setText(product.getInternalCode());
        activeInput.setChecked(product.isActive());
        productImageUrl = product.getImageUrl();
        if (duplicateId > 0 && productId == null) {
            nameInput.setText(product.getName() + " (copia)");
            internalCodeInput.setText("");
            stockInput.setText("0");
            List<ProductVariant> copies = new ArrayList<>();
            for (ProductVariant variant : variants) {
                copies.add(new ProductVariant(java.util.UUID.randomUUID().toString(),
                        variant.getSize(), variant.getColor(), 0, 0));
            }
            variants = copies;
        }
        if (!productImageUrl.isEmpty()) {
            imageButton.setText("Cambiar imagen");
            imagePreview.setVisibility(View.VISIBLE);
            Glide.with(this)
                    .load(productImageUrl)
                    .centerCrop()
                    .into(imagePreview);
        }
        markInitialFormState();
    }

    private void saveProduct() {
        if (saving) {
            return;
        }
        String name = nameInput.getText().toString().trim();
        if (!validateProductFields(name)) {
            return;
        }

        if (selectedImageUri != null) {
            uploadImageThenSave(name);
            return;
        }

        saveProductWithImage(name, productImageUrl);
    }

    private boolean validateProductFields(String name) {
        clearFieldErrors();
        EditText firstInvalid = null;

        if (name.isEmpty()) {
            firstInvalid = markFieldError(nameInput, "El nombre es obligatorio", firstInvalid);
        } else if (!ValidationUtils.hasReasonableTextLength(
                name, ValidationUtils.MAX_SHORT_TEXT_LENGTH)) {
            firstInvalid = markFieldError(nameInput, "El nombre es demasiado largo", firstInvalid);
        }
        if (!ValidationUtils.hasReasonableTextLength(descriptionInput.getText().toString(),
                ValidationUtils.MAX_LONG_TEXT_LENGTH)) {
            firstInvalid = markFieldError(descriptionInput,
                    "La descripción es demasiado larga", firstInvalid);
        }
        if (categoryInput.getText().toString().trim().isEmpty()) {
            firstInvalid = markFieldError(categoryInput,
                    "La categoría es obligatoria", firstInvalid);
        } else if (!ValidationUtils.hasReasonableTextLength(categoryInput.getText().toString(),
                ValidationUtils.MAX_SHORT_TEXT_LENGTH)) {
            firstInvalid = markFieldError(categoryInput,
                    "La categoría es demasiado larga", firstInvalid);
        }
        if (!ValidationUtils.hasReasonableTextLength(internalCodeInput.getText().toString(),
                ValidationUtils.MAX_SHORT_TEXT_LENGTH)) {
            firstInvalid = markFieldError(internalCodeInput,
                    "El código interno es demasiado largo", firstInvalid);
        }
        if (!ValidationUtils.hasReasonableTextLength(sizesInput.getText().toString(),
                ValidationUtils.MAX_SHORT_TEXT_LENGTH)) {
            firstInvalid = markFieldError(sizesInput,
                    "Las tallas son demasiado largas", firstInvalid);
        }
        if (!ValidationUtils.hasReasonableTextLength(colorsInput.getText().toString(),
                ValidationUtils.MAX_SHORT_TEXT_LENGTH)) {
            firstInvalid = markFieldError(colorsInput,
                    "Los colores son demasiado largos", firstInvalid);
        }

        firstInvalid = markNumberError(purchasePriceInput, "precio de compra", 0,
                ValidationUtils.MAX_MONEY_VALUE, firstInvalid);
        firstInvalid = markNumberError(salePriceInput, "precio de venta", 1,
                ValidationUtils.MAX_MONEY_VALUE, firstInvalid);
        firstInvalid = markNumberError(stockInput, "stock actual", 0,
                ValidationUtils.MAX_STOCK_VALUE, firstInvalid);
        firstInvalid = markNumberError(minStockInput, "stock mínimo", 0,
                ValidationUtils.MAX_STOCK_VALUE, firstInvalid);

        if (firstInvalid != null) {
            firstInvalid.requestFocus();
            showMessage("Revisa los campos marcados");
            return false;
        }
        return true;
    }

    private void uploadImageThenSave(String name) {
        setSaving(true, "Preparando y subiendo imagen...");
        byte[] imageBytes = compressSelectedImage();
        if (imageBytes == null) {
            setSaving(false, null);
            showMessage("No se pudo preparar la imagen");
            return;
        }

        store.uploadProductImage(name, imageBytes, new StoreCallback<String>() {
            @Override
            public void onSuccess(String imageUrl) {
                productImageUrl = imageUrl;
                selectedImageUri = null;
                saveProductWithImage(name, productImageUrl);
            }

            @Override
            public void onError(String message) {
                setSaving(false, null);
                showMessage(message);
            }
        });
    }

    private void saveProductWithImage(String name, String imageUrl) {
        setSaving(true, "Guardando producto...");
        String internalCode =
                internalCodeInput
                        .getText()
                        .toString()
                        .trim();

        if (internalCode.isEmpty()) {
            internalCode = generateInternalCode(name);
            internalCodeInput.setText(internalCode);
        }

        store.saveProductAsync(productId, name,
                descriptionInput.getText().toString(),
                categoryInput.getText().toString(),
                parseInt(purchasePriceInput),
                parseInt(salePriceInput),
                parseInt(stockInput),
                parseInt(minStockInput),
                sizesInput.getText().toString(),
                colorsInput.getText().toString(),
                internalCode,
                imageUrl,
                activeInput.isChecked(), variants, new StoreCallback<Product>() {
                    @Override
                    public void onSuccess(Product result) {
                        initialFormState = captureFormState();
                        formInitialized = false;
                        setSaving(false, null);
                        showMessage("Producto guardado");
                        openScreen(ProductListActivity.class);
                        finish();
                    }

                    @Override
                    public void onError(String message) {
                        setSaving(false, null);
                        showMessage(message);
                    }
                });
    }

    private void toggleAdvancedFields() {
        advancedFieldsVisible =
                !advancedFieldsVisible;
        renderAdvancedFields();
    }

    private void renderAdvancedFields() {
        int visibility =
                advancedFieldsVisible
                        ? View.VISIBLE
                        : View.GONE;

        internalCodeSection.setVisibility(
                visibility
        );
        advancedFieldsSection.setVisibility(
                visibility
        );
        advancedFieldsButton.setText(
                advancedFieldsVisible
                        ? "Ocultar detalles opcionales"
                        : "Mostrar detalles opcionales"
        );
        advancedFieldsButton.setCompoundDrawablesRelativeWithIntrinsicBounds(
                0,
                0,
                advancedFieldsVisible
                        ? R.drawable.ic_expand_less
                        : R.drawable.ic_expand_more,
                0
        );
    }

    private String generateInternalCode(
            String productName) {

        String normalized =
                Normalizer.normalize(
                                productName,
                                Normalizer.Form.NFD
                        )
                        .replaceAll(
                                "\\p{M}",
                                ""
                        )
                        .replaceAll(
                                "[^A-Za-z0-9]",
                                ""
                        )
                        .toUpperCase(
                                Locale.ROOT
                        );

        String prefix =
                normalized.length() >= 3
                        ? normalized.substring(0, 3)
                        : String.format(
                        Locale.ROOT,
                        "%-3s",
                        normalized
                ).replace(' ', 'X');

        long suffix =
                productId == null
                        ? System.currentTimeMillis() % 100000
                        : productId;

        return String.format(
                Locale.ROOT,
                "%s-%05d",
                prefix,
                suffix
        );
    }

    private byte[] compressSelectedImage() {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (InputStream boundsStream = getContentResolver().openInputStream(selectedImageUri)) {
                BitmapFactory.decodeStream(boundsStream, null, bounds);
            }

            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = calculateInSampleSize(bounds, MAX_IMAGE_SIZE, MAX_IMAGE_SIZE);
            try (InputStream imageStream = getContentResolver().openInputStream(selectedImageUri)) {
                Bitmap bitmap = BitmapFactory.decodeStream(imageStream, null, options);
                if (bitmap == null) {
                    return null;
                }
                Bitmap scaled = scaleBitmap(bitmap);
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                scaled.compress(Bitmap.CompressFormat.JPEG, IMAGE_QUALITY, output);
                if (scaled != bitmap) {
                    scaled.recycle();
                }
                bitmap.recycle();
                return output.toByteArray();
            }
        } catch (Exception exception) {
            return null;
        }
    }

    private int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        int height = options.outHeight;
        int width = options.outWidth;
        int inSampleSize = 1;

        while ((height / inSampleSize) > reqHeight || (width / inSampleSize) > reqWidth) {
            inSampleSize *= 2;
        }
        return inSampleSize;
    }

    private Bitmap scaleBitmap(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int longestSide = Math.max(width, height);
        if (longestSide <= MAX_IMAGE_SIZE) {
            return bitmap;
        }
        float scale = (float) MAX_IMAGE_SIZE / longestSide;
        int targetWidth = Math.round(width * scale);
        int targetHeight = Math.round(height * scale);
        return Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true);
    }

    private int parseInt(EditText input) {
        try {
            return Integer.parseInt(input.getText().toString().replace("$", "").replace(".", "").trim());
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private EditText markNumberError(EditText input, String label, int minValue,
            int maxValue, EditText firstInvalid) {
        String rawValue = input.getText().toString().replace("$", "").replace(".", "").trim();
        if (rawValue.isEmpty()) {
            return markFieldError(input, "Ingresa " + label, firstInvalid);
        }
        try {
            int value = Integer.parseInt(rawValue);
            if (value < minValue || value > maxValue) {
                return markFieldError(input,
                        "El " + label + " está fuera del rango permitido", firstInvalid);
            }
            return firstInvalid;
        } catch (NumberFormatException exception) {
            return markFieldError(input,
                    "Ingresa un valor numérico válido en " + label, firstInvalid);
        }
    }

    private EditText markFieldError(EditText input, String message, EditText firstInvalid) {
        input.setError(message);
        return firstInvalid == null ? input : firstInvalid;
    }

    private void clearFieldErrors() {
        EditText[] inputs = {nameInput, descriptionInput, categoryInput, purchasePriceInput,
                salePriceInput, stockInput, minStockInput, sizesInput, colorsInput,
                internalCodeInput};
        for (EditText input : inputs) {
            input.setError(null);
        }
    }

    private void setSaving(boolean isSaving, String status) {
        saving = isSaving;
        saveStatusContainer.setVisibility(isSaving ? View.VISIBLE : View.GONE);
        saveStatusText.setText(status == null ? "" : status);
        saveButton.setEnabled(!isSaving);
        imageButton.setEnabled(!isSaving);
        cancelButton.setEnabled(!isSaving);
        advancedFieldsButton.setEnabled(!isSaving);
        activeInput.setEnabled(!isSaving);

        EditText[] inputs = {nameInput, descriptionInput, categoryInput, purchasePriceInput,
                salePriceInput, stockInput, minStockInput, sizesInput, colorsInput,
                internalCodeInput};
        for (EditText input : inputs) {
            input.setEnabled(!isSaving && (input != stockInput || variants.isEmpty()));
        }
        findViewById(R.id.buttonEditVariants).setEnabled(!isSaving);
    }

    private void markInitialFormState() {
        initialFormState = captureFormState();
        formInitialized = true;
    }

    private String captureFormState() {
        return nameInput.getText() + "\n"
                + descriptionInput.getText() + "\n"
                + categoryInput.getText() + "\n"
                + purchasePriceInput.getText() + "\n"
                + salePriceInput.getText() + "\n"
                + stockInput.getText() + "\n"
                + minStockInput.getText() + "\n"
                + sizesInput.getText() + "\n"
                + colorsInput.getText() + "\n"
                + internalCodeInput.getText() + "\n"
                + activeInput.isChecked() + "\n"
                + (selectedImageUri == null ? productImageUrl : selectedImageUri.toString())
                + variantState();
    }

    private String variantState() {
        StringBuilder value = new StringBuilder();
        for (ProductVariant variant : variants) {
            value.append(variant.getId()).append(variant.getLabel()).append(variant.getStock());
        }
        return value.toString();
    }

    private void setupProductTools() {
        findViewById(R.id.buttonEditVariants).setOnClickListener(view ->
                VariantEditor.show(this, variants, result -> {
                    variants = result;
                    renderVariantSummary();
                }));
        TextWatcher watcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { renderMargin(); }
            @Override public void afterTextChanged(Editable editable) { }
        };
        purchasePriceInput.addTextChangedListener(watcher);
        salePriceInput.addTextChangedListener(watcher);
        renderVariantSummary();
        renderMargin();
        markInitialFormState();
    }

    private void renderMargin() {
        int purchase = parseInt(purchasePriceInput);
        int sale = parseInt(salePriceInput);
        double margin = sale == 0 ? 0 : 100.0 * (sale - purchase) / sale;
        ((TextView) findViewById(R.id.textProductMargin)).setText("Margen: "
                + store.formatMoney(sale - purchase) + " por unidad · "
                + String.format(new Locale("es", "CO"), "%.1f%%", margin));
    }

    private void renderVariantSummary() {
        int total = 0;
        int reserved = 0;
        for (ProductVariant variant : variants) {
            total += variant.getStock();
            reserved += variant.getReservedStock();
        }
        if (!variants.isEmpty()) {
            stockInput.setText(String.valueOf(total));
            Set<String> sizes = new LinkedHashSet<>();
            Set<String> colors = new LinkedHashSet<>();
            for (ProductVariant variant : variants) {
                sizes.add(variant.getSize());
                colors.add(variant.getColor());
            }
            sizesInput.setText(String.join(", ", sizes));
            colorsInput.setText(String.join(", ", colors));
        }
        stockInput.setEnabled(variants.isEmpty());
        ((TextView) findViewById(R.id.textVariantSummary)).setText(variants.isEmpty()
                ? "Inventario general"
                : variants.size() + " combinaciones · total " + total + " · reservado " + reserved
                        + " · disponible " + (total-reserved));
    }

    @Override protected void requestLeaveScreen(Runnable exitAction) {
        if (saving) {
            showMessage("Espera a que termine el guardado");
            return;
        }
        if (!formInitialized || initialFormState.equals(captureFormState())) {
            exitAction.run();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("¿Descartar cambios?")
                .setMessage("Los cambios realizados en el producto todavía no se han guardado.")
                .setNegativeButton("Continuar editando", null)
                .setPositiveButton("Descartar", (dialog, which) -> exitAction.run())
                .show();
    }
}
