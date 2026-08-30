package com.clarys.app;

import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;

import com.clarys.app.data.StoreCallback;
import com.clarys.app.data.SupabaseStore;
import com.clarys.app.model.CartItem;
import com.clarys.app.model.OrderRequest;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.List;

public class OrderRequestDetailActivity
        extends BaseScreenActivity {

    private static final int MAX_RECEIPT_SIZE =
            10 * 1024 * 1024;

    private SupabaseStore store;

    private int orderId;

    private OrderRequest order;

    private EditText notesInput;
    private TextView descriptionText;

    private View approveButton;
    private View rejectButton;
    private View completeButton;
    private View uploadReceiptButton;
    private View generateDescriptionButton;
    private View whatsappButton;

    private boolean automaticDescriptionRequested;


    private final ActivityResultLauncher<String>
            receiptPicker =

            registerForActivityResult(
                    new ActivityResultContracts.GetContent(),

                    uri -> {

                        if (uri != null) {
                            prepareReceipt(uri);
                        }
                    }
            );


    @Override
    protected void onCreate(
            Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        setContentView(
                R.layout.activity_order_request_detail
        );


        store =
                SupabaseStore.getInstance(this);


        orderId =
                getIntent().getIntExtra(
                        "orderId",
                        -1
                );


        if (orderId <= 0) {

            showMessage(
                    "Pedido no válido"
            );

            finish();

            return;
        }


        setupHeader(
                R.id.buttonHeaderHome,
                R.id.buttonHeaderBack
        );


        notesInput =
                findViewById(
                        R.id.inputOrderAdminNotes
                );


        descriptionText =
                findViewById(
                        R.id.textOrderAutomaticDescription
                );


        approveButton =
                findViewById(
                        R.id.buttonApproveOrder
                );


        rejectButton =
                findViewById(
                        R.id.buttonRejectOrder
                );


        completeButton =
                findViewById(
                        R.id.buttonCompleteOrder
                );


        uploadReceiptButton =
                findViewById(
                        R.id.buttonUploadOrderReceipt
                );


        generateDescriptionButton =
                findViewById(
                        R.id.buttonGenerateOrderDescription
                );


        whatsappButton =
                findViewById(
                        R.id.buttonOrderWhatsapp
                );


        // =========================================================
        // NOTAS
        // =========================================================

        findViewById(
                R.id.buttonSaveOrderNotes
        ).setOnClickListener(
                view -> saveNotes()
        );


        generateDescriptionButton
                .setOnClickListener(
                        view -> generateDescription(true)
                );


        whatsappButton
                .setOnClickListener(
                        view -> openWhatsAppChat()
                );


        // =========================================================
        // COMPROBANTE
        // =========================================================

        uploadReceiptButton
                .setOnClickListener(
                        view ->
                                receiptPicker.launch(
                                        "*/*"
                                )
                );


        // =========================================================
        // ESTADOS
        // =========================================================

        approveButton
                .setOnClickListener(
                        view ->
                                updateStatus(
                                        OrderRequest.STATUS_APPROVED
                                )
                );


        rejectButton
                .setOnClickListener(
                        view ->
                                confirmReject()
                );


        completeButton
                .setOnClickListener(
                        view ->
                                confirmComplete()
                );
    }


    // =============================================================
    // REFRESH
    // =============================================================

    @Override
    protected void onResume() {
        super.onResume();

        refreshOrder();
    }


    private void refreshOrder() {

        store.refreshOrderRequests(
                new StoreCallback<List<OrderRequest>>() {

                    @Override
                    public void onSuccess(
                            List<OrderRequest> result) {

                        order =
                                store.getOrderRequest(
                                        orderId
                                );

                        if (order == null) {

                            showMessage(
                                    "Pedido no encontrado"
                            );

                            finish();

                            return;
                        }

                        renderOrder();
                    }


                    @Override
                    public void onError(
                            String message) {

                        showMessage(message);
                    }
                }
        );
    }


    // =============================================================
    // RENDER
    // =============================================================

    private void renderOrder() {

        if (order == null) {
            return;
        }


        ((TextView) findViewById(
                R.id.textOrderDetailNumber
        )).setText(
                "Pedido #" + order.getId()
        );


        ((TextView) findViewById(
                R.id.textOrderDetailStatus
        )).setText(
                statusLabel(
                        order.getStatus()
                )
        );


        ((TextView) findViewById(
                R.id.textOrderDetailDate
        )).setText(
                formatDate(
                        order.getCreatedAt()
                )
        );


        ((TextView) findViewById(
                R.id.textOrderDetailCustomer
        )).setText(
                order.getCustomerName()
        );


        ((TextView) findViewById(
                R.id.textOrderDetailPhone
        )).setText(
                order.getCustomerPhone()
                        .isEmpty()
                        ? "Sin teléfono"
                        : order.getCustomerPhone()
        );


        ((TextView) findViewById(
                R.id.textOrderDetailUnits
        )).setText(
                order.getTotalUnits()
                        + (
                        order.getTotalUnits() == 1
                                ? " unidad"
                                : " unidades"
                )
        );


        ((TextView) findViewById(
                R.id.textOrderDetailTotal
        )).setText(
                store.formatMoney(
                        order.getTotal()
                )
        );


        notesInput.setText(
                order.getAdminNotes()
                        == null
                        ? ""
                        : order.getAdminNotes()
        );


        renderDescription();

        whatsappButton.setVisibility(
                order.getCustomerPhone() == null
                        || order.getCustomerPhone()
                        .trim()
                        .isEmpty()
                        ? View.GONE
                        : View.VISIBLE
        );


        renderItems();

        renderReceipt();

        renderActions();
    }


    private void renderDescription() {

        String savedDescription =
                order.getAiDescription() == null
                        ? ""
                        : order.getAiDescription().trim();

        if (!savedDescription.isEmpty()) {
            descriptionText.setText(
                    savedDescription
            );

            return;
        }

        descriptionText.setText(
                buildLocalDescription()
        );

        if (!automaticDescriptionRequested) {
            automaticDescriptionRequested = true;
            generateDescription(false);
        }
    }


    private void renderItems() {

        LinearLayout container =
                findViewById(
                        R.id.layoutOrderDetailItems
                );


        container.removeAllViews();


        for (CartItem item :
                order.getItems()) {

            LinearLayout row =
                    new LinearLayout(this);

            row.setOrientation(
                    LinearLayout.HORIZONTAL
            );

            row.setGravity(
                    android.view.Gravity.CENTER_VERTICAL
            );

            row.setPadding(
                    dp(12),
                    dp(12),
                    dp(12),
                    dp(12)
            );


            TextView product =
                    new TextView(this);

            product.setLayoutParams(
                    new LinearLayout.LayoutParams(
                            0,
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            1f
                    )
            );

            product.setText(
                    item.getProduct().getName()
                            + " × "
                            + item.getQuantity()
            );

            product.setTextColor(
                    getColor(
                            R.color.clarys_dark
                    )
            );

            product.setTextSize(13);

            product.setTypeface(
                    product.getTypeface(),
                    android.graphics.Typeface.BOLD
            );


            TextView price =
                    new TextView(this);

            price.setText(
                    store.formatMoney(
                            item.getSubtotal()
                    )
            );

            price.setTextColor(
                    getColor(
                            R.color.clarys_mid
                    )
            );

            price.setTextSize(13);

            price.setTypeface(
                    price.getTypeface(),
                    android.graphics.Typeface.BOLD
            );


            row.addView(product);

            row.addView(price);

            container.addView(row);
        }
    }


    private void renderReceipt() {

        TextView receiptStatus =
                findViewById(
                        R.id.textOrderReceiptStatus
                );


        boolean attached =
                order.getReceiptUrl() != null
                        && !order.getReceiptUrl()
                        .trim()
                        .isEmpty();


        receiptStatus.setText(
                attached
                        ? "Comprobante adjuntado"
                        : "Aún no hay comprobante"
        );


        ((TextView) uploadReceiptButton)
                .setText(
                        attached
                                ? "Reemplazar comprobante"
                                : "Adjuntar comprobante"
                );


        uploadReceiptButton.setVisibility(
                order.isPending()
                        || order.isApproved()
                        ? View.VISIBLE
                        : View.GONE
        );
    }


    private void renderActions() {

        boolean pending =
                order.isPending();

        boolean approved =
                order.isApproved();

        boolean manageable =
                pending || approved;


        findViewById(
                R.id.textOrderManagementTitle
        ).setVisibility(
                manageable
                        ? View.VISIBLE
                        : View.GONE
        );


        approveButton.setVisibility(
                pending
                        ? View.VISIBLE
                        : View.GONE
        );


        rejectButton.setVisibility(
                pending
                        ? View.VISIBLE
                        : View.GONE
        );


        completeButton.setVisibility(
                approved
                        ? View.VISIBLE
                        : View.GONE
        );
    }


    // =============================================================
    // NOTAS
    // =============================================================

    private void generateDescription(
            boolean requestedByUser) {

        if (order == null) {
            return;
        }

        String localDescription =
                buildLocalDescription();

        descriptionText.setText(
                localDescription
        );

        setDescriptionLoading(true);

        store.generateOrderDescriptionAsync(
                orderId,
                new StoreCallback<String>() {

                    @Override
                    public void onSuccess(
                            String description) {

                        descriptionText.setText(
                                description
                        );

                        persistDescription(
                                description,
                                requestedByUser,
                                true
                        );
                    }

                    @Override
                    public void onError(
                            String message) {

                        persistDescription(
                                localDescription,
                                requestedByUser,
                                false
                        );
                    }
                }
        );
    }


    private void persistDescription(
            String description,
            boolean requestedByUser,
            boolean generatedWithAi) {

        store.updateOrderRequestDescription(
                orderId,
                description,
                new StoreCallback<Void>() {

                    @Override
                    public void onSuccess(
                            Void result) {

                        setDescriptionLoading(false);

                        if (requestedByUser) {
                            showMessage(
                                    generatedWithAi
                                            ? "Descripción mejorada con IA"
                                            : "La IA no está disponible; se creó una descripción local"
                            );
                        }
                    }

                    @Override
                    public void onError(
                            String message) {

                        setDescriptionLoading(false);

                        if (requestedByUser) {
                            showMessage(
                                    "Descripción lista. Aplica la actualización de Supabase para conservarla"
                            );
                        }
                    }
                }
        );
    }


    private String buildLocalDescription() {
        return OrderDescriptionBuilder.build(
                order,
                store.formatMoney(
                        order == null
                                ? 0
                                : order.getTotal()
                )
        );
    }


    private void setDescriptionLoading(
            boolean loading) {

        generateDescriptionButton.setEnabled(
                !loading
        );

        ((TextView) generateDescriptionButton)
                .setText(
                        loading
                                ? "Generando descripción..."
                                : "Mejorar descripción con IA"
                );
    }


    private void openWhatsAppChat() {

        if (order == null) {
            return;
        }

        String message =
                "Hola "
                        + order.getCustomerName()
                        + ", te escribimos de "
                        + store.getBusinessName()
                        + " sobre tu pedido #"
                        + order.getId()
                        + ".";

        if (!WhatsAppUtils.openChat(
                this,
                order.getCustomerPhone(),
                message
        )) {
            showMessage(
                    "No se pudo abrir WhatsApp"
            );
        }
    }

    private void saveNotes() {

        store.updateOrderRequestNotes(
                orderId,
                notesInput
                        .getText()
                        .toString(),

                new StoreCallback<Void>() {

                    @Override
                    public void onSuccess(
                            Void result) {

                        showMessage(
                                "Notas guardadas"
                        );

                        refreshOrder();
                    }


                    @Override
                    public void onError(
                            String message) {

                        showMessage(message);
                    }
                }
        );
    }


    // =============================================================
    // COMPROBANTE
    // =============================================================

    private void prepareReceipt(
            Uri uri) {

        String mimeType =
                getContentResolver()
                        .getType(uri);


        if (mimeType == null) {

            showMessage(
                    "No se pudo identificar el archivo"
            );

            return;
        }


        boolean valid =
                mimeType.startsWith(
                        "image/"
                )
                        || "application/pdf"
                        .equalsIgnoreCase(
                                mimeType
                        );


        if (!valid) {

            showMessage(
                    "Selecciona una imagen o un archivo PDF"
            );

            return;
        }


        uploadReceiptButton.setEnabled(
                false
        );


        showMessage(
                "Preparando comprobante..."
        );


        String finalMimeType =
                mimeType;


        new Thread(() -> {

            try {

                byte[] bytes =
                        readFileBytes(uri);


                runOnUiThread(
                        () ->
                                uploadReceipt(
                                        bytes,
                                        finalMimeType
                                )
                );


            } catch (Exception exception) {

                runOnUiThread(() -> {

                    uploadReceiptButton
                            .setEnabled(true);

                    showMessage(
                            exception.getMessage() == null
                                    ? "No se pudo leer el comprobante"
                                    : exception.getMessage()
                    );
                });
            }

        }).start();
    }


    private byte[] readFileBytes(
            Uri uri) throws Exception {

        try (
                InputStream input =
                        getContentResolver()
                                .openInputStream(uri);

                ByteArrayOutputStream output =
                        new ByteArrayOutputStream()
        ) {

            if (input == null) {

                throw new Exception(
                        "No se pudo abrir el comprobante"
                );
            }


            byte[] buffer =
                    new byte[8192];


            int read;

            int total = 0;


            while ((read =
                    input.read(buffer)) != -1) {

                total += read;


                if (total >
                        MAX_RECEIPT_SIZE) {

                    throw new Exception(
                            "El comprobante no puede superar 10 MB"
                    );
                }


                output.write(
                        buffer,
                        0,
                        read
                );
            }


            return output.toByteArray();
        }
    }


    private void uploadReceipt(
            byte[] bytes,
            String mimeType) {

        store.uploadOrderReceipt(
                orderId,
                bytes,
                mimeType,

                new StoreCallback<String>() {

                    @Override
                    public void onSuccess(
                            String result) {

                        uploadReceiptButton
                                .setEnabled(true);

                        showMessage(
                                "Comprobante adjuntado"
                        );

                        refreshOrder();
                    }


                    @Override
                    public void onError(
                            String message) {

                        uploadReceiptButton
                                .setEnabled(true);

                        showMessage(message);
                    }
                }
        );
    }


    // =============================================================
    // ESTADOS
    // =============================================================

    private void updateStatus(
            String status) {

        store.updateOrderRequestStatus(
                orderId,
                status,

                new StoreCallback<Void>() {

                    @Override
                    public void onSuccess(
                            Void result) {

                        showMessage(
                                "Pedido actualizado"
                        );

                        refreshOrder();
                    }


                    @Override
                    public void onError(
                            String message) {

                        showMessage(message);
                    }
                }
        );
    }


    private void confirmReject() {

        new AlertDialog.Builder(this)
                .setTitle(
                        "Denegar pedido"
                )
                .setMessage(
                        "¿Seguro que deseas denegar este pedido?"
                )
                .setNegativeButton(
                        "Cancelar",
                        null
                )
                .setPositiveButton(
                        "Denegar",
                        (dialog, which) ->
                                updateStatus(
                                        OrderRequest.STATUS_REJECTED
                                )
                )
                .show();
    }


    private void confirmComplete() {

        new AlertDialog.Builder(this)
                .setTitle(
                        "Completar pedido"
                )
                .setMessage(
                        "¿Confirmas que este pedido fue completado?"
                )
                .setNegativeButton(
                        "Cancelar",
                        null
                )
                .setPositiveButton(
                        "Completar",
                        (dialog, which) ->
                                updateStatus(
                                        OrderRequest.STATUS_COMPLETED
                                )
                )
                .show();
    }


    // =============================================================
    // HELPERS
    // =============================================================

    private String statusLabel(
            String status) {

        if (OrderRequest.STATUS_PENDING
                .equals(status)) {

            return "PENDIENTE";
        }


        if (OrderRequest.STATUS_APPROVED
                .equals(status)) {

            return "APROBADO";
        }


        if (OrderRequest.STATUS_REJECTED
                .equals(status)) {

            return "DENEGADO";
        }


        if (OrderRequest.STATUS_COMPLETED
                .equals(status)) {

            return "COMPLETADO";
        }


        return "PEDIDO";
    }


    private String formatDate(
            String raw) {

        if (raw == null
                || raw.trim().isEmpty()) {

            return "Fecha no disponible";
        }


        String clean =
                raw.replace(
                        "T",
                        " "
                );


        if (clean.length() >= 16) {

            clean =
                    clean.substring(
                            0,
                            16
                    );
        }


        return clean;
    }


    private int dp(
            int value) {

        return Math.round(
                value
                        * getResources()
                        .getDisplayMetrics()
                        .density
        );
    }
}
