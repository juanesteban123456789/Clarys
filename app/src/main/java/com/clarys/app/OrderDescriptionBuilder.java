package com.clarys.app;

import com.clarys.app.model.CartItem;
import com.clarys.app.model.OrderRequest;

import java.util.List;

/**
 * Construye una descripción administrativa útil cuando la IA
 * no está configurada o no se encuentra disponible.
 */
final class OrderDescriptionBuilder {

    private OrderDescriptionBuilder() {
    }

    static String build(
            OrderRequest order,
            String formattedTotal) {

        if (order == null) {
            return "Valida los productos, el inventario disponible, "
                    + "el pago y la entrega antes de aprobar el pedido.";
        }

        String customerName = safe(
                order.getCustomerName(),
                "El cliente"
        );

        StringBuilder products =
                new StringBuilder();

        List<CartItem> items =
                order.getItems();

        for (int index = 0;
             index < items.size();
             index++) {

            CartItem item = items.get(index);

            if (item == null
                    || item.getProduct() == null) {
                continue;
            }

            if (products.length() > 0) {
                products.append(
                        index == items.size() - 1
                                ? " y "
                                : ", "
                );
            }

            products.append(item.getQuantity())
                    .append(
                            item.getQuantity() == 1
                                    ? " unidad de "
                                    : " unidades de "
                    )
                    .append(item.getProduct().getName());
        }

        String requestedProducts =
                products.length() == 0
                        ? "los productos indicados"
                        : products.toString();

        return customerName
                + " está solicitando "
                + requestedProducts
                + ". El valor total estimado a pagar es "
                + safe(formattedTotal, "el indicado en el pedido")
                + ". Antes de aprobar, valida que exista stock suficiente "
                + "para cada producto, confirma el método de pago y acuerda "
                + "la entrega con el cliente.";
    }

    private static String safe(
            String value,
            String fallback) {

        return value == null
                || value.trim().isEmpty()
                ? fallback
                : value.trim();
    }
}
