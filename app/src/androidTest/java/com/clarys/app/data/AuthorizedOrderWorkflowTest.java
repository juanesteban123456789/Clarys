package com.clarys.app.data;

import android.content.Context;
import android.util.Log;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.clarys.app.BuildConfig;
import com.clarys.app.model.OrderRequest;
import com.clarys.app.model.Product;
import com.clarys.app.model.ProductVariant;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

/**
 * Prueba real autorizada, deshabilitada por defecto. Crea exclusivamente un
 * producto de demostracion y conserva la venta/historial como evidencia.
 * No extrae tokens, no modifica el carrito y no elimina usuarios ni datos reales.
 * Ejecutar solo con: -e clarysLiveTest true y una sesion administrativa activa.
 */
@RunWith(AndroidJUnit4.class)
public class AuthorizedOrderWorkflowTest {
    private static final String TAG = "ClarysWorkflowTest";
    private SupabaseStore store;
    private SupabaseClient client;
    private int productId;
    private int openOrderId;

    private interface Operation<T> {
        void start(StoreCallback<T> callback) throws Exception;
    }

    private <T> T await(Operation<T> operation) throws Exception {
        CountDownLatch finished = new CountDownLatch(1);
        AtomicReference<T> value = new AtomicReference<>();
        AtomicReference<String> error = new AtomicReference<>();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            try {
                operation.start(new StoreCallback<T>() {
                    @Override public void onSuccess(T result) { value.set(result); finished.countDown(); }
                    @Override public void onError(String message) { error.set(message); finished.countDown(); }
                });
            } catch (Exception exception) {
                error.set(exception.getClass().getSimpleName()); finished.countDown();
            }
        });
        assertTrue("La operacion excedio 90 segundos", finished.await(90, TimeUnit.SECONDS));
        assertNull("Error de la app: " + error.get(), error.get());
        return value.get();
    }

    private void rejected(JSONObject body) throws Exception {
        CountDownLatch finished = new CountDownLatch(1);
        AtomicReference<String> error = new AtomicReference<>();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() ->
                client.post("rpc/submit_catalog_request", body, false, new StoreCallback<String>() {
                    @Override public void onSuccess(String result) { finished.countDown(); }
                    @Override public void onError(String message) { error.set(message); finished.countDown(); }
                }));
        assertTrue(finished.await(60, TimeUnit.SECONDS));
        assertNotNull("La solicitud invalida debe devolver un error", error.get());
        assertFalse(error.get().trim().isEmpty());
        Log.i(TAG, "Rechazo esperado: " + error.get());
    }

    private JSONObject request(String phone, String variant, int quantity) throws Exception {
        JSONObject item = new JSONObject().put("product_id", productId).put("quantity", quantity)
                .put("unit_price", 20000).put("variant_id", variant);
        return new JSONObject().put("p_customer_name", "Cliente PRUEBA SENA")
                .put("p_customer_phone", phone).put("p_items", new JSONArray().put(item))
                .put("p_submission_key", UUID.randomUUID().toString());
    }

    private JSONObject submit(JSONObject body) throws Exception {
        String reply = await(callback -> client.post("rpc/submit_catalog_request", body, false, callback));
        return new JSONArray(reply).getJSONObject(0);
    }

    private Product product() throws Exception {
        this.<java.util.List<Product>>await(callback -> store.refreshProducts(true, callback));
        Product product = store.getProduct(productId);
        assertNotNull(product);
        assertTrue(product.getName().startsWith("PRUEBA SENA "));
        return product;
    }

    private OrderRequest order(int orderId) throws Exception {
        this.<java.util.List<OrderRequest>>await(callback -> store.refreshOrderRequests(callback));
        OrderRequest order = store.getOrderRequest(orderId);
        assertNotNull(order);
        assertEquals(productId, order.getItems().get(0).getProduct().getId());
        return order;
    }

    private void status(int orderId, String status) throws Exception {
        this.<Void>await(callback -> store.updateOrderRequestStatus(orderId, status, callback));
        assertEquals(status, order(orderId).getStatus());
        Log.i(TAG, "Pedido de prueba " + orderId + ": " + status);
    }

    private void checkStock(int total, int reserved, int available) throws Exception {
        Product product = product();
        assertEquals(total, product.getTotalStock());
        assertEquals(reserved, product.getReservedStock());
        assertEquals(available, product.getStock());
        Log.i(TAG, "Stock prueba: total=" + total + ", reservado=" + reserved + ", disponible=" + available);
    }

    private void archiveTestProduct() throws Exception {
        Product testProduct = product();
        assertEquals("Antes de cerrar, no debe tener reservas", 0, testProduct.getReservedStock());
        java.util.List<ProductVariant> emptyVariants = new java.util.ArrayList<>();
        for (ProductVariant variant : testProduct.getVariants()) {
            emptyVariants.add(new ProductVariant(variant.getId(), variant.getSize(), variant.getColor(), 0, 0));
        }
        this.<Product>await(callback -> store.saveProductAsync(productId, testProduct.getName(),
                testProduct.getDescription(), testProduct.getCategory(), testProduct.getPurchasePrice(),
                testProduct.getSalePrice(), 0, testProduct.getMinStock(),
                testProduct.getSizes(), testProduct.getColors(), testProduct.getSku(),
                testProduct.getImageUrl(), false, emptyVariants, callback));
        assertFalse(product().isActive());
        assertEquals(0, product().getTotalStock());
        Log.i(TAG, "Producto de prueba archivado y stock cero: " + productId);
    }

    @Test public void authorizedReservationDeliveryAndCancellation() throws Exception {
        assumeTrue(BuildConfig.IS_ADMIN_APP && Boolean.parseBoolean(
                InstrumentationRegistry.getArguments().getString("clarysLiveTest", "false")));
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        store = SupabaseStore.getInstance(context);
        client = new SupabaseClient(SupabaseSession.getInstance(context));
        assertTrue("Inicia sesion en Clarys Gestion antes de esta prueba", store.isAuthenticated());
        assertTrue(await(callback -> store.validateAdminSession(false, callback)));
        int previousId = Integer.parseInt(InstrumentationRegistry.getArguments()
                .getString("clarysCleanupProductId", "0"));
        if (previousId > 0) {
            productId = previousId;
            Product previous = product();
            String previousSuffix = InstrumentationRegistry.getArguments().getString("clarysCleanupSuffix", "");
            assertFalse(previousSuffix.isEmpty());
            assertEquals("PRUEBA SENA " + previousSuffix, previous.getName());
            assertEquals("TEST-" + previousSuffix, previous.getSku());
            archiveTestProduct();
            productId = 0;
        }
        String suffix = Long.toString(System.currentTimeMillis());
        String firstPhone = "000" + suffix.substring(suffix.length() - 9) + "1";
        String secondPhone = "000" + suffix.substring(suffix.length() - 9) + "2";
        String firstVariant = UUID.randomUUID().toString();
        String secondVariant = UUID.randomUUID().toString();
        try {
            Product created = await(callback -> store.saveProductAsync(null, "PRUEBA SENA " + suffix,
                    "Demostracion autorizada; no es mercancia real.", "Pruebas SENA", 10000, 20000,
                    5, 0, "M,L", "Negro,Azul", "TEST-" + suffix, "", true,
                    Arrays.asList(new ProductVariant(firstVariant, "M", "Negro", 3, 0),
                            new ProductVariant(secondVariant, "L", "Azul", 2, 0)), callback));
            assertNotNull(created);
            productId = created.getId();
            Log.i(TAG, "Producto de prueba creado: " + productId);
            checkStock(5, 0, 5);

            JSONObject invalid = request(firstPhone, firstVariant, 2);
            invalid.put("p_customer_name", ""); rejected(invalid);
            rejected(request(firstPhone, firstVariant, 99));
            JSONObject body = request(firstPhone, firstVariant, 2);
            JSONObject submitted = submit(body);
            int orderId = submitted.getInt("request_id");
            openOrderId = orderId;
            assertEquals(orderId, submit(body).getInt("request_id"));
            OrderRequest pending = order(orderId);
            assertEquals(OrderRequest.STATUS_PENDING, pending.getStatus());
            assertEquals(40000L, pending.getTotal());
            assertEquals("", pending.getAdminNotes());
            assertEquals(firstVariant, pending.getItems().get(0).getVariantId());
            String trackingToken = submitted.getString("tracking_token");
            java.util.List<OrderRequest> tracked = await(callback ->
                    store.findClientOrdersByTrackingTokens(Collections.singletonList(trackingToken), callback));
            assertEquals(orderId, tracked.get(0).getId());
            String description = await(callback -> store.generateOrderDescriptionAsync(orderId, callback));
            assertTrue(description.contains("PRUEBA SENA"));
            assertTrue(description.contains("40.000"));
            assertTrue("La funcion desplegada debe incluir la variante", description.contains("Negro"));
            this.<Void>await(callback -> store.updateOrderRequestDescription(orderId, description, callback));
            status(orderId, OrderRequest.STATUS_CONTACTED);
            status(orderId, OrderRequest.STATUS_APPROVED);
            checkStock(5, 2, 3);
            assertEquals(1, product().getVariant(firstVariant).getAvailableStock());
            status(orderId, OrderRequest.STATUS_PREPARING);
            status(orderId, OrderRequest.STATUS_COMPLETED);
            openOrderId = 0;
            checkStock(3, 0, 3);
            assertEquals(2, product().getSoldUnits());
            OrderRequest completed = order(orderId);
            assertTrue(completed.getSaleId() > 0);
            assertEquals(5, completed.getEvents().size());
            status(orderId, OrderRequest.STATUS_COMPLETED);
            String saleReply = await(callback -> client.get("sales?request_id=eq." + orderId
                    + "&select=id,total", true, callback));
            JSONArray sales = new JSONArray(saleReply);
            assertEquals(1, sales.length());
            assertEquals(40000L, sales.getJSONObject(0).getLong("total"));
            assertEquals(completed.getSaleId(), sales.getJSONObject(0).getInt("id"));
            Log.i(TAG, "Venta unica de prueba: " + completed.getSaleId() + ", total=40000");

            JSONObject cancellation = submit(request(secondPhone, secondVariant, 1));
            int cancelledId = cancellation.getInt("request_id");
            openOrderId = cancelledId;
            status(cancelledId, OrderRequest.STATUS_CONTACTED);
            status(cancelledId, OrderRequest.STATUS_APPROVED);
            checkStock(3, 1, 2);
            status(cancelledId, OrderRequest.STATUS_REJECTED);
            openOrderId = 0;
            checkStock(3, 0, 3);
            assertEquals(0, order(cancelledId).getSaleId());
            String finalDescription = await(callback -> store.generateOrderDescriptionAsync(orderId, callback));
            assertTrue("La funcion desplegada debe reconocer entrega", finalDescription.contains("fue entregado"));
            this.<Void>await(callback -> store.updateOrderRequestDescription(orderId, finalDescription, callback));
            Log.i(TAG, "PASS: reserva, entrega, venta unica, cancelacion, seguimiento y descripcion real");
        } finally {
            // Solo toca el producto y pedido que acaba de crear esta ejecucion.
            if (openOrderId != 0) { status(openOrderId, OrderRequest.STATUS_REJECTED); }
            if (productId != 0) {
                archiveTestProduct();
            }
        }
    }
}
