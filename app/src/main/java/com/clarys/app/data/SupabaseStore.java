package com.clarys.app.data;

import android.content.Context;

import com.clarys.app.model.CartItem;
import com.clarys.app.model.Customer;
import com.clarys.app.model.InventoryMovement;
import com.clarys.app.model.Product;
import com.clarys.app.model.Sale;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Repositorio principal de Clarys para autenticacion, catalogo, inventario,
 * ventas, clientes y configuracion usando Supabase como backend.
 */
public class SupabaseStore {
    private static SupabaseStore instance;
    private static final String STORAGE_BUCKET_PRODUCT_IMAGES = "product-images";

    private final SupabaseSession session;
    private final SupabaseClient client;
    private final List<Product> products = new ArrayList<>();
    private final List<CartItem> cart = new ArrayList<>();
    private final List<Sale> sales = new ArrayList<>();
    private final List<Customer> customers = new ArrayList<>();
    private final List<InventoryMovement> movements = new ArrayList<>();

    private String businessName = "Taller Clarys";
    private String contactWhatsapp = "3001234567";
    private String currency = "COP";
    private int defaultMinStock = 5;

    private SupabaseStore(Context context) {
        session = SupabaseSession.getInstance(context);
        client = new SupabaseClient(session);
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

    public String getWorkshopId() {
        return session.getWorkshopId();
    }

    /**
     * Inicia sesion administrativa con correo y contrasena mediante Supabase Auth.
     */
    public void signIn(String email, String password, StoreCallback<Void> callback) {
        try {
            JSONObject body = new JSONObject()
                    .put("email", email)
                    .put("password", password);
            client.auth("token?grant_type=password", body, new StoreCallback<JSONObject>() {
                @Override
                public void onSuccess(JSONObject result) {
                    saveSessionFromAuth(result);
                    loadProfileOrCreatePending(email, callback);
                }

                @Override
                public void onError(String message) {
                    callback.onError(message);
                }
            });
        } catch (Exception exception) {
            callback.onError("No se pudo iniciar sesion");
        }
    }

    /**
     * Registra un administrador y crea el taller asociado cuando Supabase confirma la sesion.
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
                    saveSessionFromAuth(result);
                    if (!session.isAuthenticated()) {
                        session.savePendingAdmin(email, workshopName, whatsapp);
                        callback.onError("Registro creado. Confirma el correo e inicia sesion.");
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
     * Inicia sesion con Google usando el token entregado por Google Sign-In.
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
            callback.onError("Google no entrego un token valido");
            return;
        }

        try {
            JSONObject body = new JSONObject()
                    .put("provider", "google")
                    .put("id_token", idToken);
            client.auth("token?grant_type=id_token", body, new StoreCallback<JSONObject>() {
                @Override
                public void onSuccess(JSONObject result) {
                    saveSessionFromAuth(result);
                    loadProfileOrHandleGoogleAdmin(createMissingProfile, workshopName, whatsapp, callback);
                }

                @Override
                public void onError(String message) {
                    callback.onError(message);
                }
            });
        } catch (Exception exception) {
            callback.onError("No se pudo iniciar sesion con Google");
        }
    }

    private void saveSessionFromAuth(JSONObject result) {
        String accessToken = result.optString("access_token", null);
        String refreshToken = result.optString("refresh_token", null);
        JSONObject user = result.optJSONObject("user");
        String userId = user == null ? null : user.optString("id", null);
        if (accessToken != null && userId != null) {
            session.saveAuth(accessToken, refreshToken, userId);
        }
    }

    private void createWorkshopAndProfile(String workshopName, String whatsapp, StoreCallback<Void> callback) {
        try {
            JSONObject workshop = new JSONObject()
                    .put("owner_user_id", session.getUserId())
                    .put("name", safe(workshopName, "Taller Clarys"))
                    .put("whatsapp", safe(whatsapp, "3001234567"))
                    .put("is_active", true);
            client.post("workshops", workshop, true, new StoreCallback<String>() {
                @Override
                public void onSuccess(String result) {
                    try {
                        JSONArray rows = new JSONArray(result);
                        String workshopId = rows.getJSONObject(0).getString("id");
                        insertProfile(workshopId, callback);
                    } catch (Exception exception) {
                        callback.onError("No se pudo leer el taller creado");
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

    private void insertProfile(String workshopId, StoreCallback<Void> callback) {
        try {
            JSONObject profile = new JSONObject()
                    .put("user_id", session.getUserId())
                    .put("workshop_id", workshopId)
                    .put("role", "admin");
            client.post("profiles", profile, true, new StoreCallback<String>() {
                @Override
                public void onSuccess(String result) {
                    session.saveProfile(workshopId, "admin");
                    session.clearPendingAdmin();
                    loadSettingsFromWorkshop(workshopId, callback);
                }

                @Override
                public void onError(String message) {
                    callback.onError(message);
                }
            });
        } catch (Exception exception) {
            callback.onError("No se pudo crear el perfil administrador");
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
                        callback.onError("Este correo no tiene taller registrado. Usa la opcion Registrar.");
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
                            session.saveProfile(workshopId, row.optString("role", "admin"));
                            JSONObject workshop = row.optJSONObject("workshops");
                            if (workshop != null) {
                                businessName = workshop.optString("name", businessName);
                                contactWhatsapp = workshop.optString("whatsapp", contactWhatsapp);
                                currency = workshop.optString("currency", currency);
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
                                businessName = workshop.optString("name", businessName);
                                contactWhatsapp = workshop.optString("whatsapp", contactWhatsapp);
                                currency = workshop.optString("currency", currency);
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
        cart.clear();
        sales.clear();
        customers.clear();
        movements.clear();
    }

    /**
     * Obtiene productos desde Supabase para el inventario administrativo o el catalogo publico.
     */
    public void refreshProducts(boolean adminOnly, StoreCallback<List<Product>> callback) {
        String path;
        if (adminOnly) {
            if (!isAuthenticated() || session.getWorkshopId() == null) {
                callback.onError("Inicia sesion para administrar productos");
                return;
            }
            path = "products?select=*&workshop_id=eq." + session.getWorkshopId() + "&order=name.asc";
        } else {
            path = "products?select=*,workshops(name,whatsapp)&active=eq.true&order=name.asc";
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
        if (!isAuthenticated() || session.getWorkshopId() == null) {
            callback.onError("Inicia sesion para guardar productos");
            return;
        }

        try {
            JSONObject body = productJson(name, description, category, purchasePrice, salePrice, stock,
                    minStock, sizes, colors, sku, imageUrl, active);
            String path = productId == null ? "products" : "products?id=eq." + productId;
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
     * Sube la imagen de un producto a Supabase Storage y retorna su URL publica.
     */
    public void uploadProductImage(String productName, byte[] imageBytes, StoreCallback<String> callback) {
        if (!isAuthenticated() || session.getWorkshopId() == null) {
            callback.onError("Inicia sesion para subir imagenes");
            return;
        }
        if (imageBytes == null || imageBytes.length == 0) {
            callback.onError("La imagen esta vacia");
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
     * Registra un movimiento de inventario ejecutando la funcion RPC adjust_stock.
     */
    public void adjustStockAsync(int productId, String type, int quantity, String reason,
            StoreCallback<Void> callback) {
        try {
            JSONObject body = new JSONObject()
                    .put("p_product_id", productId)
                    .put("p_movement_type", type)
                    .put("p_quantity", quantity)
                    .put("p_reason", safe(reason, "Movimiento manual"));
            client.post("rpc/adjust_stock", body, true, new StoreCallback<String>() {
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
            callback.onError("Inicia sesion para registrar ventas administrativas");
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
            client.post("rpc/confirm_sale", body, true, new StoreCallback<String>() {
                @Override
                public void onSuccess(String result) {
                    cart.clear();
                    refreshAfterSale(callback);
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
     * Envia una solicitud publica de catalogo para que el taller contacte al cliente.
     */
    public void submitCatalogRequestAsync(String customerName, String phone, StoreCallback<Void> callback) {
        if (cart.isEmpty()) {
            callback.onError("Agrega productos antes de enviar la solicitud");
            return;
        }

        try {
            JSONObject body = new JSONObject()
                    .put("customer_name", safe(customerName, "Cliente catalogo"))
                    .put("customer_phone", safe(phone, ""))
                    .put("items", cartItemsJson())
                    .put("status", "new");
            client.post("contact_requests", body, false, new StoreCallback<String>() {
                @Override
                public void onSuccess(String result) {
                    cart.clear();
                    callback.onSuccess(null);
                }

                @Override
                public void onError(String message) {
                    callback.onError(message);
                }
            });
        } catch (Exception exception) {
            callback.onError("No se pudo preparar la solicitud");
        }
    }

    private JSONArray cartItemsJson() throws Exception {
        JSONArray items = new JSONArray();
        for (CartItem item : cart) {
            items.put(new JSONObject()
                    .put("product_id", item.getProduct().getId())
                    .put("quantity", item.getQuantity())
                    .put("unit_price", item.getProduct().getSalePrice()));
        }
        return items;
    }

    private void refreshAfterSale(StoreCallback<Sale> callback) {
        refreshProducts(true, new StoreCallback<List<Product>>() {
            @Override
            public void onSuccess(List<Product> ignored) {
                refreshSales(new StoreCallback<List<Sale>>() {
                    @Override
                    public void onSuccess(List<Sale> refreshedSales) {
                        refreshCustomers(new EmptyListCallback<>());
                        refreshMovements(new EmptyVoidCallback());
                        callback.onSuccess(refreshedSales.isEmpty() ? null : refreshedSales.get(0));
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
                        customers.add(new Customer(row.optString("name", "Cliente"),
                                row.optString("phone", ""), row.optInt("total_orders", 0),
                                row.optInt("total_spent", 0)));
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
                                movements.add(new InventoryMovement(
                                        product == null ? "Producto" : product.optString("name", "Producto"),
                                        row.optString("movement_type", ""),
                                        row.optInt("quantity", 0),
                                        row.optInt("previous_stock", 0),
                                        row.optInt("new_stock", 0),
                                        row.optString("reason", "")));
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
     * Guarda la configuracion principal del taller en Supabase.
     */
    public void saveSettingsAsync(String businessName, String whatsapp, String currency, int minStock,
            StoreCallback<Void> callback) {
        if (!isAuthenticated() || session.getWorkshopId() == null) {
            callback.onError("Inicia sesion para guardar configuracion");
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
            callback.onError("No se pudo preparar la configuracion");
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
                row.optString("name", "Producto"),
                row.optString("description", ""),
                row.optString("category", "General"),
                row.optInt("purchase_price", 0),
                row.optInt("sale_price", 0),
                row.optInt("stock", 0),
                row.optInt("min_stock", 0),
                row.optString("sizes", ""),
                row.optString("colors", ""),
                row.optString("sku", ""),
                row.optBoolean("active", true),
                row.optInt("sold_units", 0));
        product.setWorkshopId(row.optString("workshop_id", null));
        product.setImageUrl(row.optString("image_url", ""));
        JSONObject workshop = row.optJSONObject("workshops");
        if (workshop != null) {
            product.setWorkshopName(workshop.optString("name", ""));
            product.setWorkshopWhatsapp(workshop.optString("whatsapp", ""));
        }
        return product;
    }

    private Sale parseSale(JSONObject row) {
        JSONObject customer = row.optJSONObject("customers");
        String customerName = customer == null ? "Cliente" : customer.optString("name", "Cliente");
        String customerPhone = customer == null ? "" : customer.optString("phone", "");
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
                items.add(new CartItem(product, item.optInt("quantity", 1)));
            }
        }
        return new Sale(row.optInt("id", 0), customerName, customerPhone,
                row.optString("payment_method", "Pendiente"),
                row.optString("status", "Confirmada"),
                row.optInt("discount", 0), items);
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
        Product product = getProduct(productId);
        if (product == null || !product.isActive() || product.getStock() <= 0) {
            return false;
        }
        int safeQuantity = Math.max(1, Math.min(quantity, product.getStock()));
        for (CartItem item : cart) {
            if (item.getProduct().getId() == productId) {
                item.setQuantity(Math.min(product.getStock(), item.getQuantity() + safeQuantity));
                return true;
            }
        }
        cart.add(new CartItem(product, safeQuantity));
        return true;
    }

    public List<CartItem> getCartItems() {
        return new ArrayList<>(cart);
    }

    public void updateCartQuantity(int productId, int quantity) {
        for (int i = 0; i < cart.size(); i++) {
            CartItem item = cart.get(i);
            if (item.getProduct().getId() == productId) {
                if (quantity <= 0) {
                    cart.remove(i);
                } else {
                    item.setQuantity(Math.min(quantity, item.getProduct().getStock()));
                }
                return;
            }
        }
    }

    public int getCartTotal() {
        int total = 0;
        for (CartItem item : cart) {
            total += item.getSubtotal();
        }
        return total;
    }

    public void clearCart() {
        cart.clear();
    }

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
            if (product.isLowStock() || product.isOutOfStock()) {
                count++;
            }
        }
        return count;
    }

    public int getTodaySalesTotal() {
        int total = 0;
        for (Sale sale : sales) {
            total += sale.getTotal();
        }
        return total;
    }

    public int getInventoryValue() {
        int total = 0;
        for (Product product : products) {
            total += product.getStock() * product.getPurchasePrice();
        }
        return total;
    }

    public Product getBestSeller() {
        if (products.isEmpty()) {
            return null;
        }
        return Collections.max(products, Comparator.comparingInt(Product::getSoldUnits));
    }

    public String formatMoney(int value) {
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
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
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
}
