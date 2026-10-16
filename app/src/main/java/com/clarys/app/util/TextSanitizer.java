package com.clarys.app.util;

/**
 * Evita que valores nulos provenientes de APIs o preferencias terminen
 * mostrándose literalmente como "null" en la interfaz.
 */
public final class TextSanitizer {

    private TextSanitizer() {
    }

    public static String emptyIfNull(String value) {
        if (value == null || "null".equalsIgnoreCase(value.trim())) {
            return "";
        }
        return value;
    }

    public static String orDefault(String value, String fallback) {
        String sanitized = emptyIfNull(value);
        if (sanitized.trim().isEmpty()) {
            return emptyIfNull(fallback);
        }
        return sanitized;
    }
}
