package com.clarys.app;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

/**
 * Abre una conversación de WhatsApp a partir de un teléfono
 * almacenado en un pedido.
 */
final class WhatsAppUtils {

    private static final String WHATSAPP_PACKAGE =
            "com.whatsapp";

    private WhatsAppUtils() {
    }

    static boolean openChat(
            Context context,
            String phone,
            String message) {

        String number = normalizeNumber(phone);

        if (number.isEmpty()) {
            return false;
        }

        Uri uri = Uri.parse(
                "https://wa.me/"
                        + number
                        + "?text="
                        + Uri.encode(
                        message == null
                                ? ""
                                : message
                )
        );

        Intent whatsappIntent =
                new Intent(
                        Intent.ACTION_VIEW,
                        uri
                );

        whatsappIntent.setPackage(
                WHATSAPP_PACKAGE
        );

        try {
            context.startActivity(
                    whatsappIntent
            );

            return true;

        } catch (ActivityNotFoundException exception) {

            try {
                context.startActivity(
                        new Intent(
                                Intent.ACTION_VIEW,
                                uri
                        )
                );

                return true;

            } catch (ActivityNotFoundException ignored) {
                return false;
            }
        }
    }

    private static String normalizeNumber(
            String phone) {

        String digits =
                ValidationUtils.digitsOnly(
                        phone
                );

        if (digits.startsWith("00")) {
            digits = digits.substring(2);
        }

        // Los móviles colombianos suelen guardarse localmente con 10 dígitos.
        if (digits.length() == 10
                && digits.startsWith("3")) {
            digits = "57" + digits;
        }

        return digits;
    }
}
