package com.clarys.app;

import android.util.Patterns;

/**
 * Validaciones transversales aplicadas antes de enviar formularios a las APIs.
 * Cumple el papel de control previo o middleware dentro del cliente Android.
 */
final class ValidationUtils {
    static final int MAX_SHORT_TEXT_LENGTH = 80;
    static final int MAX_LONG_TEXT_LENGTH = 500;
    static final int MAX_MONEY_VALUE = 100000000;
    static final int MAX_STOCK_VALUE = 100000;

    private ValidationUtils() {
    }

    static boolean isValidEmail(String email) {
        return email != null && email.length() <= MAX_SHORT_TEXT_LENGTH
                && Patterns.EMAIL_ADDRESS.matcher(email).matches();
    }

    static boolean isValidPhone(String phone) {
        String digits = digitsOnly(phone);
        return digits.length() >= 7 && digits.length() <= 15;
    }

    /**
     * Exige una contraseña de registro con longitud y variedad suficientes.
     * El inicio de sesión conserva compatibilidad con cuentas existentes.
     */
    static boolean isStrongPassword(String password) {
        if (password == null || password.length() < 8
                || password.length() > MAX_SHORT_TEXT_LENGTH) {
            return false;
        }

        boolean hasUppercase = false;
        boolean hasLowercase = false;
        boolean hasDigit = false;
        boolean hasSpecialCharacter = false;
        for (int index = 0; index < password.length(); index++) {
            char character = password.charAt(index);
            hasUppercase |= Character.isUpperCase(character);
            hasLowercase |= Character.isLowerCase(character);
            hasDigit |= Character.isDigit(character);
            hasSpecialCharacter |= !Character.isLetterOrDigit(character)
                    && !Character.isWhitespace(character);
        }
        return hasUppercase && hasLowercase && hasDigit && hasSpecialCharacter;
    }

    static String digitsOnly(String value) {
        return value == null ? "" : value.replaceAll("[^0-9]", "");
    }

    static boolean hasReasonableTextLength(String value, int maxLength) {
        return value == null || value.trim().length() <= maxLength;
    }
}
