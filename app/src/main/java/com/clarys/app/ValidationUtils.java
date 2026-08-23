package com.clarys.app;

import android.util.Patterns;

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

    static String digitsOnly(String value) {
        return value == null ? "" : value.replaceAll("[^0-9]", "");
    }

    static boolean hasReasonableTextLength(String value, int maxLength) {
        return value == null || value.trim().length() <= maxLength;
    }
}
