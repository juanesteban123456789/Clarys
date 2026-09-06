package com.clarys.app.data;

import android.content.Context;
import android.content.SharedPreferences;

import com.clarys.app.util.TextSanitizer;

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

    private final SharedPreferences preferences;

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

        preferences.edit()
                .putString(
                        KEY_LAST_NAME,
                        safe(name)
                )
                .putString(
                        KEY_LAST_PHONE,
                        safe(phone)
                )
                .apply();
    }

    public void savePhone(String phone) {
        preferences.edit()
                .putString(
                        KEY_LAST_PHONE,
                        safe(phone)
                )
                .apply();
    }

    public String getLastName() {
        return TextSanitizer.emptyIfNull(
                preferences.getString(KEY_LAST_NAME, "")
        );
    }

    public String getLastPhone() {
        return TextSanitizer.emptyIfNull(
                preferences.getString(KEY_LAST_PHONE, "")
        );
    }

    private String safe(String value) {
        return TextSanitizer.emptyIfNull(value).trim();
    }
}
