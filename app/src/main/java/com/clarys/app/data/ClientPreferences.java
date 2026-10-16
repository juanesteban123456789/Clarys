package com.clarys.app.data;

import android.content.Context;
import android.content.SharedPreferences;

import com.clarys.app.util.TextSanitizer;

import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Conserva localmente los datos de contacto que el cliente usa
 * en el carrito y en la consulta pública de pedidos.
 */
public final class ClientPreferences {

    private static final String PREFS_NAME =
            "clarys_client_orders";

    private static final String KEY_LAST_PHONE =
            "last_phone";

    private static final String KEY_LAST_NAME =
            "last_name";

    private static final String KEY_TRACKING_TOKENS =
            "tracking_tokens";

    private static final int MAX_TRACKING_TOKENS = 20;

    private final SharedPreferences preferences;
    private final SessionCipher valueCipher = new SessionCipher();

    public ClientPreferences(Context context) {
        preferences = context
                .getApplicationContext()
                .getSharedPreferences(
                        PREFS_NAME,
                        Context.MODE_PRIVATE
                );
    }

    public void saveContact(
            String name,
            String phone) {

        try {
            preferences.edit()
                    .putString(KEY_LAST_NAME, valueCipher.encrypt(safe(name)))
                    .putString(KEY_LAST_PHONE, valueCipher.encrypt(safe(phone)))
                    .apply();
        } catch (GeneralSecurityException exception) {
            clearContact();
        }
    }

    public void savePhone(String phone) {
        try {
            preferences.edit()
                    .putString(KEY_LAST_PHONE, valueCipher.encrypt(safe(phone)))
                    .apply();
        } catch (GeneralSecurityException exception) {
            preferences.edit().remove(KEY_LAST_PHONE).apply();
        }
    }

    public String getLastName() {
        return readSecureValue(KEY_LAST_NAME);
    }

    public String getLastPhone() {
        return readSecureValue(KEY_LAST_PHONE);
    }

    /** Guarda los últimos pedidos creados desde esta instalación. */
    public void saveTrackingToken(String token) {
        if (!isValidUuid(token)) {
            return;
        }

        Set<String> tokens = new LinkedHashSet<>(getTrackingTokens());
        tokens.remove(token);
        tokens.add(token);
        while (tokens.size() > MAX_TRACKING_TOKENS) {
            tokens.remove(tokens.iterator().next());
        }
        saveSecureValue(KEY_TRACKING_TOKENS, String.join(",", tokens));
    }

    public List<String> getTrackingTokens() {
        List<String> tokens = new ArrayList<>();
        String storedTokens = readSecureValue(KEY_TRACKING_TOKENS);
        if (storedTokens.isEmpty()) {
            return tokens;
        }

        for (String token : storedTokens.split(",")) {
            if (isValidUuid(token)) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    private String safe(String value) {
        return TextSanitizer.emptyIfNull(value).trim();
    }

    private void saveSecureValue(String key, String value) {
        try {
            preferences.edit()
                    .putString(key, valueCipher.encrypt(safe(value)))
                    .apply();
        } catch (GeneralSecurityException exception) {
            preferences.edit().remove(key).apply();
        }
    }

    private String readSecureValue(String key) {
        String storedValue = preferences.getString(key, "");
        if (storedValue == null || storedValue.isEmpty()) {
            return "";
        }

        try {
            if (valueCipher.isEncrypted(storedValue)) {
                return valueCipher.decrypt(storedValue);
            }

            preferences.edit()
                    .putString(key, valueCipher.encrypt(storedValue))
                    .apply();
            return storedValue;
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            preferences.edit().remove(key).apply();
            return "";
        }
    }

    private void clearContact() {
        preferences.edit()
                .remove(KEY_LAST_NAME)
                .remove(KEY_LAST_PHONE)
                .apply();
    }

    private boolean isValidUuid(String value) {
        if (value == null || value.trim().isEmpty()) {
            return false;
        }
        try {
            UUID.fromString(value.trim());
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
