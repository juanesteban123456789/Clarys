package com.clarys.app;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import com.clarys.app.data.StoreCallback;
import com.clarys.app.data.SupabaseStore;
import com.clarys.app.model.Product;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.text.Normalizer;
import java.util.Locale;

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
    private Button advancedFieldsButton;
    private View internalCodeSection;
    private View advancedFieldsSection;
    private boolean advancedFieldsVisible;

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
            showMessage("Imagen lista para subir al guardar");
        });

        setupHeader(R.id.buttonHeaderHome, R.id.buttonHeaderBack);
        nameInput = findViewById(R.id.inputProductName);
        descriptionInput = findViewById(R.id.inputProductDescription);
        categoryInput = findViewById(R.id.inputProductCategory);
        purchasePriceInput = findViewById(R.id.inputProductPurchasePrice);
        salePriceInput = findViewById(R.id.inputProductSalePrice);
        stockInput = findViewById(R.id.inputProductStock);
        minStockInput = findViewById(R.id.inputProductMinStock);
        sizesInput = findViewById(R.id.inputProductSizes);
        colorsInput = findViewById(R.id.inputProductColors);
        internalCodeInput = findViewById(R.id.inputProductInternalCode);
        activeInput = findViewById(R.id.checkProductActive);
        advancedFieldsButton = findViewById(R.id.buttonToggleProductAdvanced);
        internalCodeSection = findViewById(R.id.layoutProductInternalCode);
        advancedFieldsSection = findViewById(R.id.layoutProductAdvancedFields);
        advancedFieldsButton.setOnClickListener(view -> toggleAdvancedFields());
        renderAdvancedFields();

        int extraProductId = getIntent().getIntExtra("productId", -1);
        productId = extraProductId == -1 ? null : extraProductId;

        imageButton = findViewById(R.id.buttonProductImage);
        imageButton.setOnClickListener(view -> imagePicker.launch("image/*"));
        findViewById(R.id.buttonSaveProductForm).setOnClickListener(view -> saveProduct());
        bindNavigation(R.id.buttonCancelProductForm, ProductListActivity.class);
        renderProduct();
    }

    private void renderProduct() {
        if (productId == null) {
            minStockInput.setText(String.valueOf(store.getDefaultMinStock()));
            activeInput.setChecked(true);
            return;
        }

        Product product = store.getProduct(productId);
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
        stockInput.setText(String.valueOf(product.getStock()));
        minStockInput.setText(String.valueOf(product.getMinStock()));
        sizesInput.setText(product.getSizes());
        colorsInput.setText(product.getColors());
        internalCodeInput.setText(product.getInternalCode());
        activeInput.setChecked(product.isActive());
        productImageUrl = product.getImageUrl();
        if (!productImageUrl.isEmpty()) {
            imageButton.setText("Cambiar imagen");
        }
    }

    private void saveProduct() {
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
        if (name.isEmpty()) {
            showMessage("El nombre es obligatorio");
            return false;
        }
        if (!ValidationUtils.hasReasonableTextLength(name, ValidationUtils.MAX_SHORT_TEXT_LENGTH)) {
            showMessage("El nombre es demasiado largo");
            return false;
        }
        if (!ValidationUtils.hasReasonableTextLength(descriptionInput.getText().toString(),
                ValidationUtils.MAX_LONG_TEXT_LENGTH)) {
            showMessage("La descripción es demasiado larga");
            return false;
        }
        if (categoryInput.getText().toString().trim().isEmpty()) {
            showMessage("La categoría es obligatoria");
            return false;
        }
        if (!ValidationUtils.hasReasonableTextLength(categoryInput.getText().toString(),
                ValidationUtils.MAX_SHORT_TEXT_LENGTH)) {
            showMessage("La categoría es demasiado larga");
            return false;
        }
        if (!ValidationUtils.hasReasonableTextLength(internalCodeInput.getText().toString(),
                ValidationUtils.MAX_SHORT_TEXT_LENGTH)) {
            showMessage("El código interno es demasiado largo");
            return false;
        }
        if (!ValidationUtils.hasReasonableTextLength(sizesInput.getText().toString(),
                ValidationUtils.MAX_SHORT_TEXT_LENGTH)) {
            showMessage("Las tallas son demasiado largas");
            return false;
        }
        if (!ValidationUtils.hasReasonableTextLength(colorsInput.getText().toString(),
                ValidationUtils.MAX_SHORT_TEXT_LENGTH)) {
            showMessage("Los colores son demasiado largos");
            return false;
        }
        return validateNumber(purchasePriceInput, "precio de compra", 0, ValidationUtils.MAX_MONEY_VALUE)
                && validateNumber(salePriceInput, "precio de venta", 1, ValidationUtils.MAX_MONEY_VALUE)
                && validateNumber(stockInput, "stock actual", 0, ValidationUtils.MAX_STOCK_VALUE)
                && validateNumber(minStockInput, "stock mínimo", 0, ValidationUtils.MAX_STOCK_VALUE);
    }

    private void uploadImageThenSave(String name) {
        byte[] imageBytes = compressSelectedImage();
        if (imageBytes == null) {
            showMessage("No se pudo preparar la imagen");
            return;
        }

        showMessage("Subiendo imagen...");
        store.uploadProductImage(name, imageBytes, new StoreCallback<String>() {
            @Override
            public void onSuccess(String imageUrl) {
                productImageUrl = imageUrl;
                selectedImageUri = null;
                saveProductWithImage(name, productImageUrl);
            }

            @Override
            public void onError(String message) {
                showMessage(message);
            }
        });
    }

    private void saveProductWithImage(String name, String imageUrl) {
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
                activeInput.isChecked(), new StoreCallback<Product>() {
                    @Override
                    public void onSuccess(Product result) {
                        showMessage("Producto guardado");
                        openScreen(ProductListActivity.class);
                        finish();
                    }

                    @Override
                    public void onError(String message) {
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

    private boolean validateNumber(EditText input, String label, int minValue, int maxValue) {
        String rawValue = input.getText().toString().replace("$", "").replace(".", "").trim();
        if (rawValue.isEmpty()) {
            showMessage("Ingresa " + label);
            return false;
        }
        try {
            int value = Integer.parseInt(rawValue);
            if (value < minValue || value > maxValue) {
                showMessage("El " + label + " está fuera del rango permitido");
                return false;
            }
            return true;
        } catch (NumberFormatException exception) {
            showMessage("Ingresa un valor numérico válido en " + label);
            return false;
        }
    }
}
