package com.clarys.app.data;

import android.content.Context;
import android.util.Log;

import com.clarys.app.model.CartItem;
import com.clarys.app.model.Customer;
import com.clarys.app.model.InventoryMovement;
import com.clarys.app.model.OrderRequest;
import com.clarys.app.model.OrderEvent;
import com.clarys.app.model.Product;
import com.clarys.app.model.ProductVariant;
import com.clarys.app.model.Sale;
import com.clarys.app.util.TextSanitizer;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;


/**
 * Repositorio y servicio principal de Clarys. Define los recursos consumidos,
 * aplica reglas de negocio y transforma las respuestas JSON de Supabase para
 * autenticación, catálogo, inventario, ventas, clientes y configuración.
 */
public class SupabaseStore {
    private static SupabaseStore instance;
    private static final String STORAGE_BUCKET_PRODUCT_IMAGES = "product-images";

    private static final String STORAGE_BUCKET_ORDER_RECEIPTS = "order-receipts";

    private static final int MAX_RECEIPT_SIZE =
            10 * 1024 * 1024;

    private final SupabaseSession session;
    private final SupabaseClient client;
    private final ShoppingPreferences shoppingPreferences;
    private final List<Product> products = new ArrayList<>();
    private final List<CartItem> cart = new ArrayList<>();
    private final List<Sale> sales = new ArrayList<>();
    private final List<Customer> customers = new ArrayList<>();
    private final List<InventoryMovement> movements = new ArrayList<>();
    private final List<OrderRequest> orderRequests = new ArrayList<>();

    private String businessName = "Taller Clarys";
    private String contactWhatsapp = "3001234567";
    private String currency = "COP";
    private int defaultMinStock = 5;



    private SupabaseStore(Context context) {
        session = SupabaseSession.getInstance(context);
        client = new SupabaseClient(session);
        shoppingPreferences = new ShoppingPreferences(context);
        cart.addAll(shoppingPreferences.loadCart());
    }

    public static synchronized SupabaseStore getInstance(Context context) {
        if (instance == null) {
            instance = new SupabaseStore(context.getApplicationContext());
        }
        return instance;
    }

    public boolean isAuthenticated() {
        return session.isAuthenticated();
    }

    /**
     * Comprueba que la sesión administrativa siga vigente y la renueva cuando
     * el token esté próximo a vencer o corresponda la validación periódica.
     */
    public void validateAdminSession(boolean forceRefresh,
                                     StoreCallback<Boolean> callback) {
        client.ensureValidSession(forceRefresh, callback);
    }

    public String getWorkshopId() {
        return session.getWorkshopId();
    }

    public void deleteAccount(String password,StoreCallback<Void> callback) {
        if (!isAuthenticated()) { callback.onError("Inicia sesión nuevamente"); return; }
        try {
            client.invokeFunction("delete-account",new JSONObject().put("confirmation","ELIMINAR")
                    .put("password",password),true,new StoreCallback<String>() {
                @Override public void onSuccess(String result) { signOut(); callback.onSuccess(null); }
                @Override public void onError(String message) { callback.onError(message); }
            });
        } catch(Exception error) { callback.onError("No se pudo preparar la solicitud"); }
    }

    /**
     * Inicia sesión administrativa con correo y contraseña mediante Supabase Auth.
     */
    public void signIn(String email, String password, StoreCallback<Void> callback) {
        try {
            JSONObject body = new JSONObject()
                    .put("email", email)
                    .put("password", password);
            client.auth("token?grant_type=password", body, new StoreCallback<JSONObject>() {
                @Override
                public void onSuccess(JSONObject result) {
                    if (!saveSessionFromAuth(result)) {
                        callback.onError("No se pudo proteger la sesión en este dispositivo");
                        return;
                    }
                    loadProfileOrCreatePending(email, callback);
                }

                @Override
                public void onError(String message) {
                    callback.onError(message);
                }
            });
        } catch (Exception exception) {
            callback.onError("No se pudo iniciar sesión");
        }
    }

    /**
     * Registra un administrador y crea el taller asociado cuando Supabase confirma la sesión.
     */
    public void signUpAdmin(String email, String password, String workshopName, String whatsapp,
                            StoreCallback<Void> callback) {
        try {
            JSONObject body = new JSONObject()
                    .put("email", email)
                    .put("password", password);
            client.auth("signup", body, new StoreCallback<JSONObject>() {
                @Override
                public void onSuccess(JSONObject result) {
                    boolean authResponseContainsSession = result.has("access_token")
                            && result.has("refresh_token");
                    if (authResponseContainsSession && !saveSessionFromAuth(result)) {
                        callback.onError("No se pudo proteger la sesión en este dispositivo");
                        return;
                    }
                    if (!session.isAuthenticated()) {
                        session.savePendingAdmin(email, workshopName, whatsapp);
                        callback.onError("Registro creado. Confirma el correo e inicia sesión.");
                        return;
                    }
                    createWorkshopAndProfile(workshopName, whatsapp, callback);
                }

                @Override
                public void onError(String message) {
                    callback.onError(message);
                }
            });
        } catch (Exception exception) {
            callback.onError("No se pudo registrar el administrador");
        }
    }

    /**
     * Inicia sesión con Google usando el token entregado por Google Sign-In.
     */
    public void signInWithGoogle(String idToken, StoreCallback<Void> callback) {
        authenticateWithGoogle(idToken, false, "", "", callback);
    }

    /**
     * Registra un administrador con Google y vincula el usuario con un taller.
     */
    public void signUpAdminWithGoogle(String idToken, String workshopName, String whatsapp,
                                      StoreCallback<Void> callback) {
        authenticateWithGoogle(idToken, true, workshopName, whatsapp, callback);
    }

    private void authenticateWithGoogle(String idToken, boolean createMissingProfile, String workshopName,
                                        String whatsapp, StoreCallback<Void> callback) {
        if (idToken == null || idToken.trim().isEmpty()) {
            callback.onError("Google no entregó un token válido");
            return;
        }

        try {
            JSONObject body = new JSONObject()
                    .put("provider", "google")
                    .put("id_token", idToken);
            client.auth("token?grant_type=id_token", body, new StoreCallback<JSONObject>() {
                @Override
                public void onSuccess(JSONObject result) {
                    if (!saveSessionFromAuth(result)) {
                        callback.onError("No se pudo proteger la sesión en este dispositivo");
                        return;
                    }
                    loadProfileOrHandleGoogleAdmin(createMissingProfile, workshopName, whatsapp, callback);
                }

                @Override
                public void onError(String message) {
                    callback.onError(message);
                }
            });
        } catch (Exception exception) {
            callback.onError("No se pudo iniciar sesión con Google");
        }
    }

    private boolean saveSessionFromAuth(JSONObject result) {
        String accessToken = result.optString("access_token", null);
        String refreshToken = result.optString("refresh_token", null);
        JSONObject user = result.optJSONObject("user");
        String userId = user == null
                ? session.getUserId()
                : user.optString("id", session.getUserId());
        long expiresAt = result.optLong("expires_at", 0L);

        if (expiresAt <= 0L) {
            long expiresIn = result.optLong("expires_in", 0L);
            if (expiresIn > 0L) {
                expiresAt = System.currentTimeMillis() / 1000L + expiresIn;
            }
        }

        if (expiresAt <= 0L) {
            expiresAt = JwtUtils.readExpirationEpochSeconds(accessToken);
        }

        if (accessToken != null && refreshToken != null && userId != null) {
            return session.saveAuth(accessToken, refreshToken, userId, expiresAt);
        }
        return false;
    }

    private void createWorkshopAndProfile(String workshopName, String whatsapp, StoreCallback<Void> callback) {
        try {
            JSONObject body = new JSONObject()
                    .put("p_name", safe(workshopName, "Taller Clarys"))
                    .put("p_whatsapp", safe(whatsapp, ""));
            client.post("rpc/setup_admin_workshop", body, true, new StoreCallback<String>() {
                @Override
                public void onSuccess(String result) {
                    try {
                        JSONArray rows = new JSONArray(result);
                        String workshopId = rows.getJSONObject(0).getString("workshop_id");
                        session.saveProfile(workshopId, "admin");
                        session.clearPendingAdmin();
                        loadSettingsFromWorkshop(workshopId, callback);
                    } catch (Exception exception) {
                        callback.onError("No se pudo leer el taller configurado");
                    }
                }

                @Override
                public void onError(String message) {
                    callback.onError(message);
                }
            });
        } catch (Exception exception) {
            callback.onError("No se pudo crear el taller");
        }
    }

    private void loadProfileOrCreatePending(String email, StoreCallback<Void> callback) {
        loadProfile(new StoreCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                session.clearPendingAdmin();
                callback.onSuccess(result);
            }

            @Override
            public void onError(String message) {
                if (message.contains("perfil de taller") && session.hasPendingAdmin(email)) {
                    createWorkshopAndProfile(session.getPendingWorkshopName(),
                            session.getPendingWorkshopWhatsapp(), callback);
                    return;
                }
                callback.onError(message);
            }
        });
    }

    private void loadProfileOrHandleGoogleAdmin(boolean createMissingProfile, String workshopName, String whatsapp,
                                                StoreCallback<Void> callback) {
        loadProfile(new StoreCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                callback.onSuccess(result);
            }

            @Override
            public void onError(String message) {
                if (message.contains("perfil de taller")) {
                    if (createMissingProfile) {
                        createWorkshopAndProfile(workshopName, whatsapp, callback);
                    } else {
                        signOut();
                        callback.onError("Este correo no tiene taller registrado. Usa la opción Registrar.");
                    }
                    return;
                }
                callback.onError(message);
            }
        });
    }

    public void loadProfile(StoreCallback<Void> callback) {
        client.get("profiles?select=workshop_id,role,workshops(name,whatsapp,currency,default_min_stock)&limit=1",
                true, new StoreCallback<String>() {
                    @Override
                    public void onSuccess(String result) {
                        try {
                            JSONArray rows = new JSONArray(result);
                            if (rows.length() == 0) {
                                callback.onError("El usuario no tiene perfil de taller");
                                return;
                            }
                            JSONObject row = rows.getJSONObject(0);
                            String workshopId = row.getString("workshop_id");
                            session.saveProfile(workshopId, jsonText(row, "role", "admin"));
                            JSONObject workshop = row.optJSONObject("workshops");
                            if (workshop != null) {
                                businessName = jsonText(workshop, "name", businessName);
                                contactWhatsapp = jsonText(workshop, "whatsapp", contactWhatsapp);
                                currency = jsonText(workshop, "currency", currency);
                                defaultMinStock = workshop.optInt("default_min_stock", defaultMinStock);
                            }
                            callback.onSuccess(null);
                        } catch (Exception exception) {
                            callback.onError("No se pudo leer el perfil");
                        }
                    }

                    @Override
                    public void onError(String message) {
                        callback.onError(message);
                    }
                });
    }

    private void loadSettingsFromWorkshop(String workshopId, StoreCallback<Void> callback) {
        client.get("workshops?id=eq." + workshopId + "&select=name,whatsapp,currency,default_min_stock&limit=1",
                true, new StoreCallback<String>() {
                    @Override
                    public void onSuccess(String result) {
                        try {
                            JSONArray rows = new JSONArray(result);
                            if (rows.length() > 0) {
                                JSONObject workshop = rows.getJSONObject(0);
                                businessName = jsonText(workshop, "name", businessName);
                                contactWhatsapp = jsonText(workshop, "whatsapp", contactWhatsapp);
                                currency = jsonText(workshop, "currency", currency);
                                defaultMinStock = workshop.optInt("default_min_stock", defaultMinStock);
                            }
                        } catch (Exception ignored) {
                            // Defaults remain available.
                        }
                        callback.onSuccess(null);
                    }

                    @Override
                    public void onError(String message) {
                        callback.onError(message);
                    }
                });
    }

    public void signOut() {

        session.clear();

        products.clear();

        clearCart();

        sales.clear();

        customers.clear();

        movements.clear();

        orderRequests.clear();
    }

    /**
     * Obtiene productos desde Supabase para el inventario administrativo o el catálogo público.
     */
    public void refreshProducts(boolean adminOnly, StoreCallback<List<Product>> callback) {
        String path;
        if (adminOnly) {
            if (!isAuthenticated() || session.getWorkshopId() == null) {
                callback.onError("Inicia sesión para administrar productos");
                return;
            }
            path = "products?select=*&workshop_id=eq." + session.getWorkshopId() + "&order=name.asc";
        } else {
            // El catálogo público no solicita costos de compra ni métricas internas.
            path = "products?select=id,workshop_id,name,description,category,"
                    + "sale_price,stock,reserved_stock,variants,sizes,colors,sku,image_url,active,"
                    + "workshops(name,whatsapp)&active=eq.true&order=name.asc";
        }

        client.get(path, adminOnly, new StoreCallback<String>() {
            @Override
            public void onSuccess(String result) {
                try {
                    List<Product> parsed = parseProducts(new JSONArray(result));
                    products.clear();
                    products.addAll(parsed);
                    callback.onSuccess(getProducts());
                } catch (Exception exception) {
                    callback.onError("No se pudieron leer productos");
                }
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    /**
     * Crea o actualiza productos del taller autenticado en Supabase.
     */
    public void saveProductAsync(Integer productId, String name, String description, String category,
                                 int purchasePrice, int salePrice, int stock, int minStock, String sizes, String colors,
                                 String sku, String imageUrl, boolean active, StoreCallback<Product> callback) {
        saveProductAsync(productId, name, description, category, purchasePrice, salePrice, stock,
                minStock, sizes, colors, sku, imageUrl, active, null, callback);
    }

    public void saveProductAsync(Integer productId, String name, String description, String category,
                                 int purchasePrice, int salePrice, int stock, int minStock, String sizes, String colors,
                                 String sku, String imageUrl, boolean active, List<ProductVariant> variants,
                                 StoreCallback<Product> callback) {
        if (!isAuthenticated() || session.getWorkshopId() == null) {
            callback.onError("Inicia sesión para guardar productos");
            return;
        }

        try {
            JSONObject body = productJson(name, description, category, purchasePrice, salePrice, stock,
                    minStock, sizes, colors, sku, imageUrl, active);
            if (variants != null) {
                JSONArray rows = new JSONArray();
                for (ProductVariant variant : variants) {
                    rows.put(new JSONObject().put("id", variant.getId()).put("size", variant.getSize())
                            .put("color", variant.getColor()).put("stock", variant.getStock()));
                }
                body.put("variants", rows);
            }
            String path = productId == null ? "products" : "products?id=eq." + productId
                    + "&workshop_id=eq." + session.getWorkshopId();
            StoreCallback<String> response = new StoreCallback<String>() {
                @Override
                public void onSuccess(String result) {
                    try {
                        JSONArray rows = new JSONArray(result);
                        Product product = rows.length() > 0 ? parseProduct(rows.getJSONObject(0)) : null;
                        refreshProducts(true, new StoreCallback<List<Product>>() {
                            @Override
                            public void onSuccess(List<Product> ignored) {
                                callback.onSuccess(product);
                            }

                            @Override
                            public void onError(String message) {
                                callback.onSuccess(product);
                            }
                        });
                    } catch (Exception exception) {
                        callback.onError("Producto guardado, pero no se pudo leer la respuesta");
                    }
                }

                @Override
                public void onError(String message) {
                    callback.onError(message);
                }
            };

            if (productId == null) {
                body.put("workshop_id", session.getWorkshopId());
                client.post(path, body, true, response);
            } else {
                client.patch(path, body, true, response);
            }
        } catch (Exception exception) {
            callback.onError("No se pudo preparar el producto");
        }
    }

    private JSONObject productJson(String name, String description, String category, int purchasePrice,
                                   int salePrice, int stock, int minStock, String sizes, String colors, String sku,
                                   String imageUrl, boolean active) throws Exception {
        return new JSONObject()
                .put("name", safe(name, "Producto"))
                .put("description", safe(description, ""))
                .put("category", safe(category, "General"))
                .put("purchase_price", purchasePrice)
                .put("sale_price", salePrice)
                .put("stock", Math.max(0, stock))
                .put("min_stock", Math.max(0, minStock))
                .put("sizes", safe(sizes, ""))
                .put("colors", safe(colors, ""))
                .put("sku", safe(sku, ""))
                .put("image_url", safe(imageUrl, ""))
                .put("active", active);
    }

    /**
     * Sube la imagen de un producto a Supabase Storage y retorna su URL pública.
     */
    public void uploadProductImage(String productName, byte[] imageBytes, StoreCallback<String> callback) {
        if (!isAuthenticated() || session.getWorkshopId() == null) {
            callback.onError("Inicia sesión para subir imágenes");
            return;
        }
        if (imageBytes == null || imageBytes.length == 0) {
            callback.onError("La imagen está vacía");
            return;
        }

        String path = session.getWorkshopId() + "/" + System.currentTimeMillis() + "-" + slug(productName) + ".jpg";
        client.uploadStorageObject(STORAGE_BUCKET_PRODUCT_IMAGES, path, imageBytes, "image/jpeg",
                new StoreCallback<String>() {
                    @Override
                    public void onSuccess(String result) {
                        callback.onSuccess(client.publicStorageUrl(STORAGE_BUCKET_PRODUCT_IMAGES, path));
                    }

                    @Override
                    public void onError(String message) {
                        callback.onError(message);
                    }
                });
    }

    /**
     * Registra un movimiento de inventario ejecutando la función RPC adjust_stock.
     */
    public void adjustStockAsync(int productId, String type, int quantity, String reason,
                                 StoreCallback<Void> callback) {
        adjustStockAsync(productId,"",type,quantity,reason,callback);
    }

    public void adjustStockAsync(int productId,String variantId,String type,int quantity,String reason,
                                 StoreCallback<Void> callback) {
        try {
            JSONObject body = new JSONObject()
                    .put("p_product_id", productId)
                    .put("p_movement_type", type)
                    .put("p_quantity", quantity)
                    .put("p_reason", safe(reason, "Movimiento manual"));
            body.put("p_variant_id",variantId);
            client.post("rpc/adjust_variant_stock", body, true, new StoreCallback<String>() {
                @Override
                public void onSuccess(String result) {
                    refreshProducts(true, new StoreCallback<List<Product>>() {
                        @Override
                        public void onSuccess(List<Product> products) {
                            refreshMovements(callback);
                        }

                        @Override
                        public void onError(String message) {
                            callback.onSuccess(null);
                        }
                    });
                }

                @Override
                public void onError(String message) {
                    callback.onError(message);
                }
            });
        } catch (Exception exception) {
            callback.onError("No se pudo preparar el movimiento");
        }
    }

    /**
     * Confirma una venta administrativa y descuenta el stock mediante Supabase RPC.
     */
    public void confirmSaleAsync(String customerName, String phone, String paymentMethod, int discount,
                                 String status, StoreCallback<Sale> callback) {
        if (cart.isEmpty()) {
            callback.onError("Agrega productos antes de confirmar");
            return;
        }
        if (!isAuthenticated()) {
            callback.onError("Inicia sesión para registrar ventas administrativas");
            return;
        }

        try {
            JSONObject body = new JSONObject()
                    .put("p_customer_name", safe(customerName, "Cliente mostrador"))
                    .put("p_customer_phone", safe(phone, contactWhatsapp))
                    .put("p_payment_method", safe(paymentMethod, "Pendiente"))
                    .put("p_discount", Math.max(0, discount))
                    .put("p_status", safe(status, "Confirmada"))
                    .put("p_items", cartItemsJson());
            body.put("p_submission_key",shoppingPreferences.submissionKey(body.toString()));
            client.post("rpc/confirm_sale", body, true, new StoreCallback<String>() {
                @Override
                public void onSuccess(String result) {
                    int saleId;
                    try { saleId=((Number)new org.json.JSONTokener(result).nextValue()).intValue(); }
                    catch(Exception error) { callback.onError("La venta se registró; consulta su historial antes de reintentar"); return; }
                    clearCart();
                    refreshAfterSale(saleId,callback);
                }

                @Override
                public void onError(String message) {
                    callback.onError(message);
                }
            });
        } catch (Exception exception) {
            callback.onError("No se pudo preparar la venta");
        }
    }

    /**
     * Envía una solicitud pública de catálogo para que el taller contacte al cliente.
     */
    public void submitCatalogRequestAsync(
            String customerName,
            String phone,
            StoreCallback<String> callback) {

        if (cart.isEmpty()) {

            callback.onError(
                    "Agrega productos antes de enviar la solicitud"
            );

            return;
        }


        String workshopId =
                getCartWorkshopId();


        if (workshopId == null
                || workshopId.trim().isEmpty()) {

            callback.onError(
                    "No se pudo identificar el taller del pedido"
            );

            return;
        }


        if (!cartBelongsToSingleWorkshop(
                workshopId
        )) {

            callback.onError(
                    "No puedes mezclar productos de diferentes talleres"
            );

            return;
        }


        try {

            JSONObject body =
                    new JSONObject()
                            .put(
                                    "p_customer_name",
                                    safe(
                                            customerName,
                                            "Cliente catálogo"
                                    )
                            )
                            .put(
                                    "p_customer_phone",
                                    safe(
                                            phone,
                                            ""
                                    )
                            )
                            .put(
                                    "p_items",
                                    cartItemsJson()
                            );


            body.put("p_submission_key",shoppingPreferences.submissionKey(body.toString()));
            client.post(
                    "rpc/submit_catalog_request",
                    body,
                    false,

                    new StoreCallback<String>() {

                        @Override
                        public void onSuccess(
                                String result) {
                            try {
                                JSONArray rows = new JSONArray(result);
                                String trackingToken = rows.length() == 0
                                        ? ""
                                        : jsonText(rows.getJSONObject(0), "tracking_token", "");
                                if (trackingToken.isEmpty()) {
                                    callback.onError("El pedido no devolvió un código de seguimiento");
                                    return;
                                }
                                clearCart();
                                callback.onSuccess(trackingToken);
                            } catch (Exception exception) {
                                callback.onError("La respuesta del pedido no es válida");
                            }
                        }


                        @Override
                        public void onError(
                                String message) {

                            android.util.Log.e(
                                    "CLARYS_ORDER",
                                    "Error al crear pedido: "
                                            + message
                            );

                            callback.onError(
                                    message
                            );
                        }
                    }
            );


        } catch (Exception exception) {

            android.util.Log.e(
                    "CLARYS_ORDER",
                    "Error preparando pedido",
                    exception
            );

            callback.onError(
                    "No se pudo preparar la solicitud"
            );
        }
    }

    private String getCartWorkshopId() {

        if (cart.isEmpty()) {
            return null;
        }

        Product product =
                cart.get(0).getProduct();

        if (product == null) {
            return null;
        }

        return product.getWorkshopId();
    }


    private boolean cartBelongsToSingleWorkshop(
            String workshopId) {

        if (workshopId == null) {
            return false;
        }

        for (CartItem item : cart) {

            Product product =
                    item.getProduct();

            if (product == null
                    || product.getWorkshopId() == null
                    || !workshopId.equals(
                    product.getWorkshopId()
            )) {

                return false;
            }
        }

        return true;
    }

    private JSONArray cartItemsJson() throws Exception {

        JSONArray items =
                new JSONArray();

        for (CartItem item : cart) {

            Product product =
                    item.getProduct();

            items.put(
                    new JSONObject()
                            .put(
                                    "product_id",
                                    product.getId()
                            )
                            .put(
                                    "product_name",
                                    product.getName()
                            )
                            .put(
                                    "quantity",
                                    item.getQuantity()
                            )
                            .put(
                                    "unit_price",
                                    product.getSalePrice()
                            )
                            .put("variant_id", item.getVariantId())
                            .put("variant_label", item.getVariantLabel())
            );
        }

        return items;
    }

    public void refreshOrderRequests(
            StoreCallback<List<OrderRequest>> callback) {

        if (!isAuthenticated()
                || session.getWorkshopId() == null) {

            callback.onError(
                    "Acceso administrativo requerido"
            );

            return;
        }

        String path =
                "contact_requests"
                        + "?select=*,order_events(*)"
                        + "&workshop_id=eq."
                        + session.getWorkshopId()
                        + "&order=created_at.desc";

        client.get(
                path,
                true,
                new StoreCallback<String>() {

                    @Override
                    public void onSuccess(String result) {

                        try {

                            JSONArray rows =
                                    new JSONArray(result);

                            orderRequests.clear();

                            for (int i = 0;
                                 i < rows.length();
                                 i++) {

                                orderRequests.add(
                                        parseOrderRequest(
                                                rows.getJSONObject(i)
                                        )
                                );
                            }

                            callback.onSuccess(
                                    getOrderRequests()
                            );

                        } catch (Exception exception) {

                            callback.onError(
                                    "No se pudieron leer los pedidos"
                            );
                        }
                    }

                    @Override
                    public void onError(String message) {

                        callback.onError(message);
                    }
                }
        );
    }


    /**
     * Consulta pedidos públicos mediante tokens aleatorios guardados en el
     * dispositivo. El número telefónico no se utiliza como credencial.
     */
    public void findClientOrdersByTrackingTokens(
            List<String> trackingTokens,
            StoreCallback<List<OrderRequest>> callback) {

        // =========================================================
        // VALIDACIÓN
        // =========================================================

        if (trackingTokens == null || trackingTokens.isEmpty()) {

            callback.onError(
                    "No hay pedidos guardados en este dispositivo"
            );

            return;
        }


        // =========================================================
        // PREPARAR RPC
        // =========================================================

        try {

            JSONObject body =
                    new JSONObject()
                            .put(
                                    "p_tracking_tokens",
                                    new JSONArray(trackingTokens)
                            );


            client.post(
                    "rpc/get_catalog_orders_by_tokens",
                    body,

                    // La consulta es pública.
                    false,

                    new StoreCallback<String>() {

                        // =================================================
                        // RESPUESTA CORRECTA
                        // =================================================

                        @Override
                        public void onSuccess(
                                String result) {

                            try {

                                JSONArray rows =
                                        new JSONArray(
                                                result
                                        );


                                List<OrderRequest> parsed =
                                        new ArrayList<>();


                                for (int i = 0;
                                     i < rows.length();
                                     i++) {

                                    JSONObject row =
                                            rows.optJSONObject(i);


                                    if (row == null) {
                                        continue;
                                    }


                                    OrderRequest request =
                                            parseOrderRequest(
                                                    row
                                            );


                                    if (request != null) {

                                        parsed.add(
                                                request
                                        );
                                    }
                                }


                                /*
                                 * NO hacemos:
                                 *
                                 * orderRequests.clear();
                                 * orderRequests.addAll(parsed);
                                 *
                                 * porque orderRequests corresponde al
                                 * panel administrativo.
                                 */

                                callback.onSuccess(
                                        parsed
                                );


                            } catch (Exception exception) {

                                android.util.Log.e(
                                        "CLARYS_CLIENT_ORDERS",
                                        "Error leyendo pedidos del cliente",
                                        exception
                                );


                                callback.onError(
                                        "No se pudieron leer los pedidos"
                                );
                            }
                        }


                        // =================================================
                        // ERROR SUPABASE
                        // =================================================

                        @Override
                        public void onError(
                                String message) {

                            android.util.Log.e(
                                    "CLARYS_CLIENT_ORDERS",
                                    "Error consultando pedidos: "
                                            + message
                            );


                            callback.onError(
                                    message
                            );
                        }
                    }
            );


        } catch (Exception exception) {

            android.util.Log.e(
                    "CLARYS_CLIENT_ORDERS",
                    "Error preparando consulta",
                    exception
            );


            callback.onError(
                    "No se pudo preparar la consulta de pedidos"
            );
        }
    }

    private OrderRequest parseOrderRequest(
            JSONObject row) {

        List<CartItem> requestItems =
                new ArrayList<>();

        JSONArray items =
                row.optJSONArray("items");

        if (items != null) {

            for (int i = 0;
                 i < items.length();
                 i++) {

                JSONObject item =
                        items.optJSONObject(i);

                if (item == null) {
                    continue;
                }

                int productId =
                        item.optInt(
                                "product_id",
                                0
                        );

                int quantity =
                        item.optInt(
                                "quantity",
                                1
                        );

                int unitPrice =
                        item.optInt(
                                "unit_price",
                                0
                        );

                String productName = jsonText(
                        item,
                        "product_name",
                        "Producto"
                );


                Product product =
                        getProduct(productId);

                boolean inventoryKnown =
                        product != null;


                /*
                 * Pedidos antiguos pueden apuntar a un producto
                 * que todavía no está cargado en memoria.
                 */
                if (product == null) {

                    product =
                            new Product(
                                    productId,
                                    productName,
                                    "",
                                    "",
                                    0,
                                    unitPrice,
                                    0,
                                    0,
                                    "",
                                    "",
                                    "",
                                    true,
                                    0
                            );
                }

                Product snapshot = new Product(productId, productName, product.getDescription(),
                        product.getCategory(), product.getPurchasePrice(), unitPrice,
                        product.getTotalStock(), product.getMinStock(), product.getSizes(),
                        product.getColors(), product.getSku(), product.isActive(), product.getSoldUnits());
                snapshot.setReservedStock(product.getReservedStock());
                snapshot.setVariants(product.getVariants());
                CartItem requestItem = new CartItem(snapshot, quantity, inventoryKnown);
                requestItem.setVariant(jsonText(item, "variant_id", ""), jsonText(item, "variant_label", ""));
                requestItems.add(requestItem);
            }
        }

        OrderRequest request = new OrderRequest(
                row.optInt("id", 0),

                jsonText(row, "workshop_id", ""),

                jsonText(row, "customer_name", "Cliente"),

                jsonText(row, "customer_phone", ""),

                requestItems,

                jsonText(row, "status", OrderRequest.STATUS_PENDING),

                jsonText(row, "ai_description", ""),

                jsonText(row, "admin_notes", ""),

                jsonText(row, "receipt_url", ""),

                jsonText(row, "created_at", ""),

                jsonText(row, "updated_at", ""),

                jsonText(row, "approved_at", ""),

                jsonText(row, "rejected_at", ""),

                jsonText(row, "completed_at", "")
        );
        request.setSaleId(row.optInt("sale_id",0));
        List<OrderEvent> events = new ArrayList<>();
        JSONArray history = row.optJSONArray("order_events");
        if (history != null) {
            for (int index=0; index<history.length(); index++) {
                JSONObject event=history.optJSONObject(index);
                if (event != null) {
                    events.add(new OrderEvent(jsonText(event,"status",""),jsonText(event,"actor_id",""),
                            jsonText(event,"created_at","")));
                }
            }
        }
        events.sort(Comparator.comparing(OrderEvent::getCreatedAt));
        request.setEvents(events);
        return request;
    }

    public List<OrderRequest> getOrderRequests() {

        return new ArrayList<>(
                orderRequests
        );
    }


    public OrderRequest getOrderRequest(
            int orderId) {

        for (OrderRequest request :
                orderRequests) {

            if (request.getId() == orderId) {

                return request;
            }
        }

        return null;
    }


    public int getPendingOrderCount() {

        int count = 0;

        for (OrderRequest request :
                orderRequests) {

            if (request.isPending()) {
                count++;
            }
        }

        return count;
    }

    public List<OrderRequest> getOrderRequestsByStatus(
            String status) {

        if (status == null
                || status.trim().isEmpty()
                || "all".equalsIgnoreCase(status)) {

            return getOrderRequests();
        }


        List<OrderRequest> result =
                new ArrayList<>();


        for (OrderRequest request :
                orderRequests) {

            if (status.equalsIgnoreCase(
                    request.getStatus()
            )) {

                result.add(request);
            }
        }


        return result;
    }

    public void updateOrderRequestStatus(
            int orderId,
            String newStatus,
            StoreCallback<Void> callback) {

        if (!isAuthenticated()
                || session.getWorkshopId() == null) {

            callback.onError(
                    "Acceso administrativo requerido"
            );

            return;
        }


        if (!isValidOrderStatus(newStatus)) {

            callback.onError(
                    "Estado de pedido no válido"
            );

            return;
        }


        try {

            JSONObject body =
                    new JSONObject();


            body.put(
                    "status",
                    newStatus
            );


            body.put(
                    "updated_at",
                    currentTimestamp()
            );


            if (OrderRequest.STATUS_APPROVED
                    .equals(newStatus)) {

                body.put(
                        "approved_at",
                        currentTimestamp()
                );
            }


            if (OrderRequest.STATUS_REJECTED
                    .equals(newStatus)) {

                body.put(
                        "rejected_at",
                        currentTimestamp()
                );
            }


            if (OrderRequest.STATUS_COMPLETED
                    .equals(newStatus)) {

                body.put(
                        "completed_at",
                        currentTimestamp()
                );
            }


            String path =
                    "contact_requests"
                            + "?id=eq."
                            + orderId
                            + "&workshop_id=eq."
                            + session.getWorkshopId();


            client.patch(
                    path,
                    body,
                    true,
                    new StoreCallback<String>() {

                        @Override
                        public void onSuccess(
                                String result) {

                            refreshOrderRequests(
                                    new StoreCallback<List<OrderRequest>>() {

                                        @Override
                                        public void onSuccess(
                                                List<OrderRequest> result) {

                                            callback.onSuccess(null);
                                        }

                                        @Override
                                        public void onError(
                                                String message) {

                                            /*
                                             * El cambio ya se realizó.
                                             * Solo falló el refresco local.
                                             */
                                            callback.onSuccess(null);
                                        }
                                    }
                            );
                        }


                        @Override
                        public void onError(
                                String message) {

                            callback.onError(message);
                        }
                    }
            );

        } catch (Exception exception) {

            callback.onError(
                    "No se pudo actualizar el pedido"
            );
        }
    }

    private boolean isValidOrderStatus(
            String status) {

        return OrderRequest.STATUS_PENDING
                .equals(status)

                || OrderRequest.STATUS_CONTACTED
                .equals(status)

                || OrderRequest.STATUS_APPROVED
                .equals(status)

                || OrderRequest.STATUS_PREPARING
                .equals(status)

                || OrderRequest.STATUS_REJECTED
                .equals(status)

                || OrderRequest.STATUS_COMPLETED
                .equals(status);
    }

    private String currentTimestamp() {

        java.text.SimpleDateFormat format =
                new java.text.SimpleDateFormat(
                        "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
                        Locale.US
                );

        return format.format(
                new Date()
        );
    }

    private void refreshAfterSale(int saleId,StoreCallback<Sale> callback) {
        refreshProducts(true, new StoreCallback<List<Product>>() {
            @Override
            public void onSuccess(List<Product> ignored) {
                refreshSales(new StoreCallback<List<Sale>>() {
                    @Override
                    public void onSuccess(List<Sale> refreshedSales) {
                        refreshCustomers(new EmptyListCallback<>());
                        refreshMovements(new EmptyVoidCallback());
                        callback.onSuccess(getSale(saleId));
                    }

                    @Override
                    public void onError(String message) {
                        callback.onError(message);
                    }
                });
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    /**
     * Consulta las ventas registradas junto con cliente e items relacionados.
     */
    public void refreshSales(StoreCallback<List<Sale>> callback) {
        client.get("sales?select=*,customers(name,phone),sale_items(*,products(*))&order=created_at.desc",
                true, new StoreCallback<String>() {
                    @Override
                    public void onSuccess(String result) {
                        try {
                            sales.clear();
                            JSONArray rows = new JSONArray(result);
                            for (int i = 0; i < rows.length(); i++) {
                                sales.add(parseSale(rows.getJSONObject(i)));
                            }
                            callback.onSuccess(getSales());
                        } catch (Exception exception) {
                            callback.onError("No se pudieron leer ventas");
                        }
                    }

                    @Override
                    public void onError(String message) {
                        callback.onError(message);
                    }
                });
    }

    /**
     * Consulta los clientes calculados por Supabase para los reportes administrativos.
     */
    public void refreshCustomers(StoreCallback<List<Customer>> callback) {
        client.get("customers?select=*&order=name.asc", true, new StoreCallback<String>() {
            @Override
            public void onSuccess(String result) {
                try {
                    customers.clear();
                    JSONArray rows = new JSONArray(result);
                    for (int i = 0; i < rows.length(); i++) {
                        JSONObject row = rows.getJSONObject(i);
                        customers.add(new Customer(jsonText(row, "name", "Cliente"),
                                jsonText(row, "phone", ""), row.optInt("total_orders", 0),
                                row.optLong("total_spent", 0)));
                    }
                    callback.onSuccess(getCustomers());
                } catch (Exception exception) {
                    callback.onError("No se pudieron leer clientes");
                }
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    /**
     * Consulta los movimientos recientes de inventario del taller autenticado.
     */
    public void refreshMovements(StoreCallback<Void> callback) {
        client.get("inventory_movements?select=*,products(name)&order=created_at.desc&limit=30",
                true, new StoreCallback<String>() {
                    @Override
                    public void onSuccess(String result) {
                        try {
                            movements.clear();
                            JSONArray rows = new JSONArray(result);
                            for (int i = 0; i < rows.length(); i++) {
                                JSONObject row = rows.getJSONObject(i);
                                JSONObject product = row.optJSONObject("products");
                                InventoryMovement movement=new InventoryMovement(
                                        product == null ? "Producto" : jsonText(product, "name", "Producto"),
                                        jsonText(row, "movement_type", ""),
                                        row.optInt("quantity", 0),
                                        row.optInt("previous_stock", 0),
                                        row.optInt("new_stock", 0),
                                        jsonText(row, "reason", ""));
                                movement.setCreatedAt(jsonText(row,"created_at",""));
                                movements.add(movement);
                            }
                            callback.onSuccess(null);
                        } catch (Exception exception) {
                            callback.onError("No se pudieron leer movimientos");
                        }
                    }

                    @Override
                    public void onError(String message) {
                        callback.onError(message);
                    }
                });
    }

    /**
     * Guarda la configuración principal del taller en Supabase.
     */
    public void saveSettingsAsync(String businessName, String whatsapp, String currency, int minStock,
                                  StoreCallback<Void> callback) {
        if (!isAuthenticated() || session.getWorkshopId() == null) {
            callback.onError("Inicia sesión para guardar configuración");
            return;
        }
        try {
            JSONObject body = new JSONObject()
                    .put("name", safe(businessName, "Taller Clarys"))
                    .put("whatsapp", safe(whatsapp, "3001234567"))
                    .put("currency", safe(currency, "COP"))
                    .put("default_min_stock", Math.max(0, minStock));
            client.patch("workshops?id=eq." + session.getWorkshopId(), body, true, new StoreCallback<String>() {
                @Override
                public void onSuccess(String result) {
                    SupabaseStore.this.businessName = safe(businessName, "Taller Clarys");
                    contactWhatsapp = safe(whatsapp, "3001234567");
                    SupabaseStore.this.currency = safe(currency, "COP");
                    defaultMinStock = Math.max(0, minStock);
                    callback.onSuccess(null);
                }

                @Override
                public void onError(String message) {
                    callback.onError(message);
                }
            });
        } catch (Exception exception) {
            callback.onError("No se pudo preparar la configuración");
        }
    }

    private List<Product> parseProducts(JSONArray rows) throws Exception {
        List<Product> parsed = new ArrayList<>();
        for (int i = 0; i < rows.length(); i++) {
            parsed.add(parseProduct(rows.getJSONObject(i)));
        }
        return parsed;
    }

    private Product parseProduct(JSONObject row) {
        Product product = new Product(row.optInt("id", 0),
                jsonText(row, "name", "Producto"),
                jsonText(row, "description", ""),
                jsonText(row, "category", "General"),
                row.optInt("purchase_price", 0),
                row.optInt("sale_price", 0),
                row.optInt("stock", 0),
                row.optInt("min_stock", 0),
                jsonText(row, "sizes", ""),
                jsonText(row, "colors", ""),
                jsonText(row, "sku", ""),
                row.optBoolean("active", true),
                row.optInt("sold_units", 0));
        product.setWorkshopId(jsonText(row, "workshop_id", ""));
        product.setImageUrl(jsonText(row, "image_url", ""));
        product.setReservedStock(row.optInt("reserved_stock", 0));
        product.setVariants(parseVariants(row.optJSONArray("variants")));
        JSONObject workshop = row.optJSONObject("workshops");
        if (workshop != null) {
            product.setWorkshopName(jsonText(workshop, "name", ""));
            product.setWorkshopWhatsapp(jsonText(workshop, "whatsapp", ""));
        }
        return product;
    }

    private List<ProductVariant> parseVariants(JSONArray rows) {
        List<ProductVariant> variants = new ArrayList<>();
        if (rows != null) {
            for (int index = 0; index < rows.length(); index++) {
                JSONObject row = rows.optJSONObject(index);
                if (row != null) {
                    variants.add(new ProductVariant(jsonText(row, "id", ""), jsonText(row, "size", ""),
                            jsonText(row, "color", ""), row.optInt("stock", 0), row.optInt("reserved_stock", 0)));
                }
            }
        }
        return variants;
    }

    private Sale parseSale(JSONObject row) {
        JSONObject customer = row.optJSONObject("customers");
        String customerName = customer == null ? "Cliente" : jsonText(customer, "name", "Cliente");
        String customerPhone = customer == null ? "" : jsonText(customer, "phone", "");
        List<CartItem> items = new ArrayList<>();
        JSONArray saleItems = row.optJSONArray("sale_items");
        if (saleItems != null) {
            for (int i = 0; i < saleItems.length(); i++) {
                JSONObject item = saleItems.optJSONObject(i);
                if (item == null) {
                    continue;
                }
                JSONObject productJson = item.optJSONObject("products");
                Product product = productJson == null
                        ? new Product(item.optInt("product_id", 0), "Producto", "", "", 0,
                        item.optInt("unit_price", 0), 0, 0, "", "", "", true, 0)
                        : parseProduct(productJson);
                product.setSalePrice(item.optInt("unit_price", 0));
                if (item.has("unit_cost")) { product.setPurchasePrice(item.optInt("unit_cost", 0)); }
                if (item.has("product_name") && !item.isNull("product_name")) {
                    product.setName(jsonText(item, "product_name", product.getName()));
                }
                CartItem saleItem = new CartItem(product, item.optInt("quantity", 1));
                saleItem.setVariant(jsonText(item, "variant_id", ""), jsonText(item, "variant_label", ""));
                items.add(saleItem);
            }
        }
        Sale sale = new Sale(row.optInt("id", 0), customerName, customerPhone,
                jsonText(row, "payment_method", "Pendiente"),
                jsonText(row, "status", "Confirmada"),
                row.optInt("discount", 0), items);
        sale.setCreatedAt(jsonText(row, "created_at", ""));
        return sale;
    }

    public List<Product> getProducts() {
        return new ArrayList<>(products);
    }

    public List<Product> searchProducts(String query, String filter) {
        String cleanQuery = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        String cleanFilter = filter == null ? "Todos" : filter;
        List<Product> result = new ArrayList<>();
        for (Product product : products) {
            boolean matchesText = cleanQuery.isEmpty()
                    || product.getName().toLowerCase(Locale.ROOT).contains(cleanQuery)
                    || product.getCategory().toLowerCase(Locale.ROOT).contains(cleanQuery)
                    || product.getSku().toLowerCase(Locale.ROOT).contains(cleanQuery);
            boolean matchesFilter = "Todos".equals(cleanFilter)
                    || ("Stock bajo".equals(cleanFilter) && product.isLowStock())
                    || ("Agotados".equals(cleanFilter) && product.isOutOfStock())
                    || ("Inactivos".equals(cleanFilter) && !product.isActive());
            if (matchesText && matchesFilter) {
                result.add(product);
            }
        }
        return result;
    }

    public Product getProduct(int productId) {
        for (Product product : products) {
            if (product.getId() == productId) {
                return product;
            }
        }
        return null;
    }

    public boolean addToCart(int productId, int quantity) {
        return addToCart(productId, "", quantity);
    }

    public boolean addToCart(int productId, String variantId, int quantity) {
        Product product = getProduct(productId);
        if (product == null || !product.isActive() || product.getStock() <= 0) {
            return false;
        }
        ProductVariant variant = product.getVariant(variantId);
        if (!product.getVariants().isEmpty() && variant == null) { return false; }
        int available = variant == null ? product.getStock() : variant.getAvailableStock();
        if (quantity <= 0 || available < quantity) { return false; }
        int safeQuantity = quantity;
        for (CartItem item : cart) {
            if (item.getProduct().getId() == productId && item.getVariantId().equals(variantId)) {
                if (item.getQuantity() + safeQuantity > available) { return false; }
                item.setQuantity(item.getQuantity() + safeQuantity);
                shoppingPreferences.saveCart(cart);
                return true;
            }
        }
        if (!cart.isEmpty() && !cart.get(0).getProduct().getWorkshopId().equals(product.getWorkshopId())) {
            return false;
        }
        if (cart.size() >= 30) { return false; }
        CartItem added = new CartItem(product, safeQuantity);
        added.setVariant(variantId, variant == null ? "" : variant.getLabel());
        cart.add(added);
        shoppingPreferences.saveCart(cart);
        return true;
    }

    public List<CartItem> getCartItems() {
        return new ArrayList<>(cart);
    }

    public void updateCartQuantity(int productId, int quantity) {
        updateCartQuantity(productId, "", quantity);
    }

    public void updateCartQuantity(int productId, String variantId, int quantity) {
        for (int i = 0; i < cart.size(); i++) {
            CartItem item = cart.get(i);
            if (item.getProduct().getId() == productId && item.getVariantId().equals(variantId)) {
                if (quantity <= 0) {
                    cart.remove(i);
                } else {
                    int available = item.getAvailableStock();
                    if (available <= 0) { return; }
                    item.setQuantity(Math.min(quantity, available));
                }
                shoppingPreferences.saveCart(cart);
                return;
            }
        }
    }

    public long getCartTotal() {
        long total = 0;
        for (CartItem item : cart) {
            total += item.getSubtotal();
        }
        return total;
    }

    public void clearCart() {
        cart.clear();
        shoppingPreferences.saveCart(cart);
    }

    /** Relee el catalogo antes de confirmar; informa cambios para que el cliente revise el total. */
    public void revalidateCart(StoreCallback<List<String>> callback) {
        refreshProducts(false, new StoreCallback<List<Product>>() {
            @Override public void onSuccess(List<Product> result) {
                List<String> changes = new ArrayList<>();
                List<CartItem> refreshed = new ArrayList<>();
                for (CartItem oldItem : cart) {
                    Product current = getProduct(oldItem.getProduct().getId());
                    if (current == null || !current.isActive()) {
                        changes.add(oldItem.getProduct().getName() + ": ya no está disponible");
                        continue;
                    }
                    CartItem item = new CartItem(current, oldItem.getQuantity());
                    ProductVariant variant = current.getVariant(oldItem.getVariantId());
                    if (!current.getVariants().isEmpty() && variant == null) {
                        changes.add(current.getName() + ": selecciona nuevamente talla y color");
                        continue;
                    }
                    item.setVariant(oldItem.getVariantId(), variant == null ? "" : variant.getLabel());
                    int available = item.getAvailableStock();
                    if (available <= 0) {
                        changes.add(current.getName() + ": agotado");
                        continue;
                    }
                    if (available < item.getQuantity()) {
                        item.setQuantity(available);
                        changes.add(current.getName() + ": cantidad reducida a " + available);
                    }
                    if (current.getSalePrice() != oldItem.getProduct().getSalePrice()) {
                        changes.add(current.getName() + ": precio actualizado a " + formatMoney(current.getSalePrice()));
                    }
                    refreshed.add(item);
                }
                cart.clear();
                cart.addAll(refreshed);
                shoppingPreferences.saveCart(cart);
                callback.onSuccess(changes);
            }
            @Override public void onError(String message) { callback.onError(message); }
        });
    }

    public boolean isFavorite(int productId) { return shoppingPreferences.isFavorite(productId); }
    public boolean toggleFavorite(int productId) { return shoppingPreferences.toggleFavorite(productId); }

    public List<Sale> getSales() {
        return new ArrayList<>(sales);
    }

    public Sale getSale(int saleId) {
        for (Sale sale : sales) {
            if (sale.getId() == saleId) {
                return sale;
            }
        }
        return null;
    }

    public List<Customer> getCustomers() {
        return new ArrayList<>(customers);
    }

    public List<InventoryMovement> getMovements() {
        return new ArrayList<>(movements);
    }

    public int getLowStockCount() {
        int count = 0;
        for (Product product : products) {
            if (product.needsStockReplenishment()) {
                count++;
            }
        }
        return count;
    }

    public long getTodaySalesTotal() {
        long total = 0;
        for (Sale sale : com.clarys.app.util.SalesPeriod.filter(sales,1,System.currentTimeMillis(),
                java.util.TimeZone.getDefault())) {
            total += sale.getTotal();
        }
        return total;
    }

    public long getInventoryValue() {
        long total = 0;
        for (Product product : products) {
            total += (long) product.getTotalStock() * product.getPurchasePrice();
        }
        return total;
    }

    public Product getBestSeller() {
        if (products.isEmpty()) {
            return null;
        }
        Product best = Collections.max(products, Comparator.comparingInt(Product::getSoldUnits));
        return best.getSoldUnits() == 0 ? null : best;
    }

    public String formatMoney(long value) {
        NumberFormat formatter = NumberFormat.getCurrencyInstance(new Locale("es", "CO"));
        formatter.setMaximumFractionDigits(0);
        return formatter.format(value);
    }

    public String getBusinessName() {
        return businessName;
    }

    public String getContactWhatsapp() {
        return contactWhatsapp;
    }

    public String getCurrency() {
        return currency;
    }

    public int getDefaultMinStock() {
        return defaultMinStock;
    }

    private String safe(String value, String fallback) {
        String sanitized = TextSanitizer.emptyIfNull(value).trim();
        return sanitized.isEmpty() ? fallback : sanitized;
    }

    private String jsonText(JSONObject object, String key, String fallback) {
        if (object == null || object.isNull(key)) {
            return TextSanitizer.emptyIfNull(fallback);
        }
        return TextSanitizer.orDefault(object.optString(key, fallback), fallback);
    }

    private String slug(String value) {
        String clean = safe(value, "producto")
                .toLowerCase(Locale.US)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        return clean.isEmpty() ? "producto" : clean;
    }

    private static class EmptyListCallback<T> implements StoreCallback<List<T>> {
        @Override
        public void onSuccess(List<T> result) {
        }

        @Override
        public void onError(String message) {
        }
    }

    private static class EmptyVoidCallback implements StoreCallback<Void> {
        @Override
        public void onSuccess(Void result) {
        }

        @Override
        public void onError(String message) {
        }
    }

    // =============================================================
    // PEDIDOS: NOTAS Y COMPROBANTES
    // =============================================================

    /**
     * Solicita a una Edge Function la descripción administrativa
     * generada con reglas locales y con el inventario actual.
     */
    public void generateOrderDescriptionAsync(
            int orderId,
            StoreCallback<String> callback) {

        if (!isAuthenticated()
                || session.getWorkshopId() == null) {

            callback.onError(
                    "Acceso administrativo requerido"
            );

            return;
        }

        try {
            JSONObject body =
                    new JSONObject()
                            .put(
                                    "order_id",
                                    orderId
                            );

            client.invokeFunction(
                    "generate-order-description",
                    body,
                    true,
                    new StoreCallback<String>() {

                        @Override
                        public void onSuccess(
                                String result) {

                            try {
                                String description = jsonText(
                                        new JSONObject(result),
                                        "description",
                                        ""
                                ).trim();

                                if (description.isEmpty()) {
                                    callback.onError(
                                    "El servicio no devolvió una descripción"
                                    );

                                    return;
                                }

                                callback.onSuccess(
                                        description
                                );

                            } catch (Exception exception) {
                                callback.onError(
                                    "La respuesta del servicio no es válida"
                                );
                            }
                        }

                        @Override
                        public void onError(
                                String message) {

                            callback.onError(message);
                        }
                    }
            );

        } catch (Exception exception) {
            callback.onError(
                    "No se pudo preparar la descripción automática"
            );
        }
    }

    /**
     * Guarda la descripción automática separada de las notas
     * privadas que escribe el administrador.
     */
    public void updateOrderRequestDescription(
            int orderId,
            String description,
            StoreCallback<Void> callback) {

        if (!isAuthenticated()
                || session.getWorkshopId() == null) {

            callback.onError(
                    "Acceso administrativo requerido"
            );

            return;
        }

        try {
            JSONObject body =
                    new JSONObject()
                            .put(
                                    "ai_description",
                                    description == null
                                            ? ""
                                            : description.trim()
                            )
                            .put(
                                    "updated_at",
                                    currentTimestamp()
                            );

            patchOrderRequest(
                    orderId,
                    body,
                    callback
            );

        } catch (Exception exception) {
            callback.onError(
                    "No se pudo guardar la descripción"
            );
        }
    }

    public void updateOrderRequestNotes(
            int orderId,
            String notes,
            StoreCallback<Void> callback) {

        if (!isAuthenticated() || session.getWorkshopId() == null) {
            callback.onError("Acceso administrativo requerido");
            return;
        }

        try {
            JSONObject body = new JSONObject()
                    .put("admin_notes", notes == null ? "" : notes.trim())
                    .put("updated_at", currentTimestamp());

            patchOrderRequest(orderId, body, callback);

        } catch (Exception exception) {
            callback.onError("No se pudieron guardar las notas");
        }
    }

    public void uploadOrderReceipt(
            int orderId,
            byte[] fileBytes,
            String mimeType,
            StoreCallback<String> callback) {

        if (!isAuthenticated() || session.getWorkshopId() == null) {
            callback.onError("Acceso administrativo requerido");
            return;
        }

        if (fileBytes == null || fileBytes.length == 0) {
            callback.onError("El comprobante está vacío");
            return;
        }

        if (fileBytes.length > MAX_RECEIPT_SIZE) {
            callback.onError("El comprobante no puede superar 10 MB");
            return;
        }

        String safeMime = mimeType == null
                ? "application/octet-stream"
                : mimeType;

        String extension = receiptExtension(safeMime);

        String storagePath =
                session.getWorkshopId()
                        + "/order-"
                        + orderId
                        + "/"
                        + System.currentTimeMillis()
                        + "."
                        + extension;

        client.uploadStorageObject(
                STORAGE_BUCKET_ORDER_RECEIPTS,
                storagePath,
                fileBytes,
                safeMime,
                new StoreCallback<String>() {
                    @Override
                    public void onSuccess(String result) {
                        saveOrderReceiptPath(orderId, storagePath, callback);
                    }

                    @Override
                    public void onError(String message) {
                        callback.onError(message);
                    }
                }
        );
    }

    private void saveOrderReceiptPath(
            int orderId,
            String storagePath,
            StoreCallback<String> callback) {

        try {
            JSONObject body = new JSONObject()
                    .put("receipt_url", storagePath)
                    .put("updated_at", currentTimestamp());

            patchOrderRequest(
                    orderId,
                    body,
                    new StoreCallback<Void>() {
                        @Override
                        public void onSuccess(Void result) {
                            callback.onSuccess(storagePath);
                        }

                        @Override
                        public void onError(String message) {
                            callback.onError(message);
                        }
                    }
            );

        } catch (Exception exception) {
            callback.onError(
                    "El archivo fue enviado, pero no se pudo asociar al pedido"
            );
        }
    }

    private void patchOrderRequest(
            int orderId,
            JSONObject body,
            StoreCallback<Void> callback) {

        if (!isAuthenticated() || session.getWorkshopId() == null) {
            callback.onError("Acceso administrativo requerido");
            return;
        }

        String path =
                "contact_requests"
                        + "?id=eq."
                        + orderId
                        + "&workshop_id=eq."
                        + session.getWorkshopId();

        client.patch(
                path,
                body,
                true,
                new StoreCallback<String>() {
                    @Override
                    public void onSuccess(String result) {
                        refreshOrderRequests(
                                new StoreCallback<List<OrderRequest>>() {
                                    @Override
                                    public void onSuccess(List<OrderRequest> result) {
                                        callback.onSuccess(null);
                                    }

                                    @Override
                                    public void onError(String message) {
                                        // El PATCH ya fue aplicado; solo falló el refresco local.
                                        callback.onSuccess(null);
                                    }
                                }
                        );
                    }

                    @Override
                    public void onError(String message) {
                        callback.onError(message);
                    }
                }
        );
    }

    private String receiptExtension(String mimeType) {
        if ("application/pdf".equalsIgnoreCase(mimeType)) {
            return "pdf";
        }

        if ("image/png".equalsIgnoreCase(mimeType)) {
            return "png";
        }

        if ("image/webp".equalsIgnoreCase(mimeType)) {
            return "webp";
        }

        return "jpg";
    }

}
