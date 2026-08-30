package com.clarys.app.data;

import android.util.Base64;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;

/**
 * Utilidades mínimas para consultar metadatos no sensibles de un JWT.
 */
public final class JwtUtils {

    private JwtUtils() {
    }

    /**
     * Obtiene la fecha de expiración Unix del token sin validar su firma.
     * La validación real continúa siendo responsabilidad de Supabase.
     */
    public static long readExpirationEpochSeconds(String token) {
        if (token == null || token.trim().isEmpty()) {
            return 0L;
        }

        try {
            String[] parts = token.split("\\.");
            if (parts.length < 2) {
                return 0L;
            }

            byte[] decoded = Base64.decode(parts[1],
                    Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
            JSONObject payload = new JSONObject(new String(decoded, StandardCharsets.UTF_8));
            return payload.optLong("exp", 0L);
        } catch (Exception ignored) {
            return 0L;
        }
    }
}
