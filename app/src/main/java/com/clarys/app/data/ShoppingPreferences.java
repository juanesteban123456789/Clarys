package com.clarys.app.data;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import com.clarys.app.model.CartItem;
import com.clarys.app.model.Product;
import com.clarys.app.model.ProductVariant;

import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Conserva el carrito y favoritos de esta instalación sin solicitar una cuenta. */
public final class ShoppingPreferences {
    private final SharedPreferences preferences;
    private final SessionCipher cipher = new SessionCipher();

    public ShoppingPreferences(Context context) {
        preferences = context.getApplicationContext().getSharedPreferences("clarys_shopping", Context.MODE_PRIVATE);
    }

    public void saveCart(List<CartItem> items) {
        if (items.isEmpty()) { preferences.edit().remove("submission_key").remove("submission_hash").apply(); }
        JSONArray array = new JSONArray();
        try {
            for (CartItem item : items) {
                Product product = item.getProduct();
                JSONArray variants = new JSONArray();
                for (ProductVariant variant : product.getVariants()) {
                    variants.put(new JSONObject().put("id", variant.getId()).put("size", variant.getSize())
                            .put("color", variant.getColor()).put("stock", variant.getStock())
                            .put("reserved_stock", variant.getReservedStock()));
                }
                array.put(new JSONObject().put("id", product.getId()).put("name", product.getName())
                        .put("price", product.getSalePrice()).put("stock", product.getTotalStock())
                        .put("reserved", product.getReservedStock()).put("workshop", product.getWorkshopId())
                        .put("workshop_name", product.getWorkshopName()).put("active", product.isActive())
                        .put("variants", variants).put("quantity", item.getQuantity())
                        .put("variant_id", item.getVariantId()).put("variant_label", item.getVariantLabel()));
            }
            preferences.edit().putString("cart", cipher.encrypt(array.toString())).apply();
        } catch (GeneralSecurityException | org.json.JSONException exception) {
            preferences.edit().remove("cart").apply();
        }
    }

    public List<CartItem> loadCart() {
        List<CartItem> items = new ArrayList<>();
        try {
            String stored = preferences.getString("cart", "");
            if (stored == null || stored.isEmpty()) { return items; }
            JSONArray array = new JSONArray(cipher.decrypt(stored));
            for (int index = 0; index < Math.min(array.length(), 30); index++) {
                JSONObject row = array.getJSONObject(index);
                Product product = new Product(row.getInt("id"), row.getString("name"), "", "", 0,
                        row.getInt("price"), row.getInt("stock"), 0, "", "", "", row.getBoolean("active"), 0);
                product.setWorkshopId(row.getString("workshop"));
                product.setWorkshopName(row.optString("workshop_name", ""));
                product.setReservedStock(row.optInt("reserved", 0));
                List<ProductVariant> variants = new ArrayList<>();
                JSONArray rawVariants = row.optJSONArray("variants");
                if (rawVariants != null) {
                    for (int position = 0; position < rawVariants.length(); position++) {
                        JSONObject variant = rawVariants.getJSONObject(position);
                        variants.add(new ProductVariant(variant.getString("id"), variant.getString("size"),
                                variant.getString("color"), variant.getInt("stock"), variant.optInt("reserved_stock", 0)));
                    }
                }
                product.setVariants(variants);
                CartItem item = new CartItem(product, row.getInt("quantity"));
                item.setVariant(row.optString("variant_id", ""), row.optString("variant_label", ""));
                items.add(item);
            }
        } catch (GeneralSecurityException | org.json.JSONException | IllegalArgumentException exception) {
            items.clear();
            preferences.edit().remove("cart").apply();
        }
        return items;
    }

    public boolean isFavorite(int productId) { return favorites().contains(String.valueOf(productId)); }
    public boolean toggleFavorite(int productId) {
        Set<String> ids = favorites();
        String id = String.valueOf(productId);
        boolean selected = !ids.remove(id);
        if (selected) { ids.add(id); }
        preferences.edit().putStringSet("favorites", ids).apply();
        return selected;
    }
    private Set<String> favorites() {
        return new HashSet<>(preferences.getStringSet("favorites", new HashSet<>()));
    }

    /** Mantiene el mismo identificador al reintentar una solicitud que pudo llegar al servidor. */
    public String submissionKey(String payload) throws GeneralSecurityException {
        byte[] bytes = java.security.MessageDigest.getInstance("SHA-256")
                .digest(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        StringBuilder hash = new StringBuilder();
        for (byte value : bytes) { hash.append(String.format(java.util.Locale.ROOT,"%02x",value & 255)); }
        String oldHash = preferences.getString("submission_hash","");
        String stored = preferences.getString("submission_key","");
        if (hash.toString().equals(oldHash) && stored != null && !stored.isEmpty()) {
            return cipher.decrypt(stored);
        }
        String key = UUID.randomUUID().toString();
        preferences.edit().putString("submission_hash",hash.toString())
                .putString("submission_key",cipher.encrypt(key)).apply();
        return key;
    }
}
