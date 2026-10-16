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
import com.clarys.app.model.OrderEvent;
import com.clarys.app.util.OrderStatusUtils;
import com.clarys.app.util.SalesPeriod;

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
                        view -> advanceOrder()
                );


        rejectButton
                .setOnClickListener(
                        view ->
                                confirmCancel()
                );


        completeButton
                .setOnClickListener(
                        view ->
                                confirmDelivery()
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

        findViewById(R.id.buttonOrderDocument).setEnabled(!order.isRejected());
        findViewById(R.id.buttonOrderDocument).setOnClickListener(view -> {
            android.content.Intent intent = new android.content.Intent(this, SalesDocumentFormActivity.class);
            intent.putExtra("orderId", orderId);
            startActivity(intent);
        });


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


        renderTimeline();


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


    private void renderTimeline() {

        LinearLayout timeline =
                findViewById(R.id.layoutOrderTimeline);

        timeline.removeAllViews();

        if (order.isRejected()) {
            timeline.addView(createTimelineStep(
                    OrderStatusUtils.label(order.getStatus()),
                    true,
                    true
            ));
        } else {
            int currentIndex =
                    OrderStatusUtils.progressIndex(order.getStatus());

            String[] statuses =
                    OrderStatusUtils.workflowStatuses();

            for (int index = 0; index < statuses.length; index++) {
                timeline.addView(createTimelineStep(
                        OrderStatusUtils.label(statuses[index]),
                        index <= currentIndex,
                        false
                ));
            }
        }

        String updatedAt = order.getUpdatedAt();
        if (updatedAt == null || updatedAt.trim().isEmpty()) {
            updatedAt = order.getCreatedAt();
        }

        ((TextView) findViewById(R.id.textOrderTimelineUpdated))
                .setText("Última actualización: " + formatDate(updatedAt));
        LinearLayout audit = findViewById(R.id.layoutOrderAuditTrail);
        audit.removeAllViews();
        for (OrderEvent event : order.getEvents()) {
            TextView entry = new TextView(this);
            entry.setText(OrderStatusUtils.label(event.getStatus())+" · "+formatDate(event.getCreatedAt())
                    +" · "+(event.getActorId().isEmpty() ? "Cliente" : "Administración"));
            entry.setTextSize(11);
            entry.setTextColor(getColor(R.color.clarys_muted));
            entry.setPadding(0,dp(4),0,dp(4));
            audit.addView(entry);
        }
        if (order.getSaleId()>0) {
            TextView sale = new TextView(this);
            sale.setText("Venta registrada #" + order.getSaleId());
            sale.setTextColor(getColor(R.color.clarys_mid));
            audit.addView(sale);
        }
    }


    private TextView createTimelineStep(
            String label,
            boolean reached,
            boolean cancelled) {

        TextView step = new TextView(this);
        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        dp(42)
                );
        params.setMarginEnd(dp(8));
        step.setLayoutParams(params);
        step.setGravity(android.view.Gravity.CENTER);
        step.setPadding(dp(14), 0, dp(14), 0);
        step.setText(label);
        step.setTextSize(10);
        step.setTypeface(step.getTypeface(), android.graphics.Typeface.BOLD);
        step.setBackgroundResource(cancelled
                ? R.drawable.bg_soft_card
                : reached
                ? R.drawable.bg_category_chip_active
                : R.drawable.bg_category_chip);
        step.setTextColor(getColor(cancelled
                ? R.color.clarys_danger
                : reached
                ? R.color.clarys_light
                : R.color.clarys_muted));
        step.setContentDescription(
                "Estado " + label.toLowerCase(java.util.Locale.getDefault())
        );
        return step;
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
                OrderStatusUtils.isActive(order.getStatus())
                        ? View.VISIBLE
                        : View.GONE
        );
    }


    private void renderActions() {

        boolean manageable =
                OrderStatusUtils.isActive(order.getStatus());

        boolean preparing =
                order.isPreparing();


        findViewById(
                R.id.textOrderManagementTitle
        ).setVisibility(
                manageable
                        ? View.VISIBLE
                        : View.GONE
        );


        approveButton.setVisibility(
                manageable && !preparing
                        ? View.VISIBLE
                        : View.GONE
        );

        ((TextView) approveButton).setText(
                OrderStatusUtils.nextActionLabel(order.getStatus())
        );


        rejectButton.setVisibility(
                manageable
                        ? View.VISIBLE
                        : View.GONE
        );

        ((TextView) rejectButton).setText(
                "Cancelar pedido"
        );


        completeButton.setVisibility(
                preparing
                        ? View.VISIBLE
                        : View.GONE
        );

        ((TextView) completeButton).setText(
                "Marcar como entregado"
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
            boolean generatedByService) {

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
                                    generatedByService
                                            ? "Descripción automática actualizada"
                                            : "Se creó una descripción local del pedido"
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
                                : "Actualizar descripción"
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

    private void advanceOrder() {

        String nextStatus =
                OrderStatusUtils.nextStatus(order.getStatus());

        if (nextStatus == null) {
            showMessage("Este pedido ya no tiene una transición disponible");
            return;
        }

        updateStatus(nextStatus);
    }

    private void updateStatus(
            String status) {

        setStatusActionsEnabled(false);

        store.updateOrderRequestStatus(
                orderId,
                status,

                new StoreCallback<Void>() {

                    @Override
                    public void onSuccess(
                            Void result) {

                        setStatusActionsEnabled(true);

                        showMessage(
                                "Pedido actualizado: "
                                        + OrderStatusUtils.label(status)
                        );

                        refreshOrder();
                    }


                    @Override
                    public void onError(
                            String message) {

                        setStatusActionsEnabled(true);

                        showMessage(message);
                    }
                }
        );
    }

    private void setStatusActionsEnabled(
            boolean enabled) {

        approveButton.setEnabled(enabled);
        rejectButton.setEnabled(enabled);
        completeButton.setEnabled(enabled);
    }


    private void confirmCancel() {

        new AlertDialog.Builder(this)
                .setTitle(
                        "Cancelar pedido"
                )
                .setMessage(
                        "¿Seguro que deseas cancelar este pedido? Esta acción cerrará su flujo."
                )
                .setNegativeButton(
                        "Cancelar",
                        null
                )
                .setPositiveButton(
                        "Cancelar pedido",
                        (dialog, which) ->
                                updateStatus(
                                        OrderRequest.STATUS_REJECTED
                                )
                )
                .show();
    }


    private void confirmDelivery() {

        new AlertDialog.Builder(this)
                .setTitle(
                        "Confirmar entrega"
                )
                .setMessage(
                        "¿Confirmas que el pedido fue entregado al cliente?"
                )
                .setNegativeButton(
                        "Cancelar",
                        null
                )
                .setPositiveButton(
                        "Marcar entregado",
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
        return OrderStatusUtils.label(status);
    }


    private String formatDate(
            String raw) {
        return SalesPeriod.display(raw);
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
