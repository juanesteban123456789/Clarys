package com.clarys.app;

import com.clarys.app.model.CartItem;
import com.clarys.app.model.OrderRequest;

import java.util.ArrayList;
import java.util.List;

/**
 * Construye una descripción administrativa del pedido sin depender
 * de servicios de inteligencia artificial externos.
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

        List<String> products =
                new ArrayList<>();

        List<String> insufficientStock =
                new ArrayList<>();

        List<String> inactiveProducts =
                new ArrayList<>();

        List<String> unknownStock =
                new ArrayList<>();

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

            String productName = safe(
                    item.getProduct().getName(),
                    "producto"
            );

            products.add(
                    item.getQuantity()
                            + (
                            item.getQuantity() == 1
                                    ? " unidad de "
                                    : " unidades de "
                    )
                            + productName + (item.getVariantLabel().isEmpty() ? "" : " (" + item.getVariantLabel() + ")")
            );

            if (!item.isInventoryKnown()) {
                unknownStock.add(productName);
            } else if (!item.getProduct().isActive()) {
                inactiveProducts.add(productName);
            } else if (!order.isApproved() && !order.isPreparing() && !order.isCompleted()
                    && item.getAvailableStock() < item.getQuantity()) {
                insufficientStock.add(
                        productName
                                + " (solicita "
                                + item.getQuantity()
                                + " y hay "
                                + item.getProduct().getStock()
                                + ")"
                );
            }
        }

        String requestedProducts =
                naturalList(
                        products,
                        "los productos indicados"
                );

        List<String> inventoryMessages =
                new ArrayList<>();

        if (!insufficientStock.isEmpty()) {
            inventoryMessages.add(
                    "El inventario es insuficiente para "
                            + naturalList(insufficientStock, "")
            );
        }

        if (!inactiveProducts.isEmpty()) {
            inventoryMessages.add(
                    naturalList(inactiveProducts, "")
                            + (
                            inactiveProducts.size() == 1
                                    ? " no está activo"
                                    : " no están activos"
                    )
            );
        }

        if (!unknownStock.isEmpty()) {
            inventoryMessages.add(
                    "se debe verificar manualmente el stock de "
                            + naturalList(unknownStock, "")
            );
        }

        if (inventoryMessages.isEmpty()) {
            inventoryMessages.add(
                    "El inventario disponible es suficiente para atender "
                            + "las cantidades solicitadas"
            );
        }

        return customerName
                + " está solicitando "
                + requestedProducts
                + ". El valor total estimado a pagar es "
                + safe(formattedTotal, "el indicado en el pedido")
                + ". "
                + joinSentences(inventoryMessages)
                + ". Confirma el método de pago y acuerda la entrega "
                + "antes de aprobar el pedido.";
    }

    private static String naturalList(
            List<String> values,
            String fallback) {

        if (values == null || values.isEmpty()) {
            return fallback;
        }

        if (values.size() == 1) {
            return values.get(0);
        }

        return String.join(
                ", ",
                values.subList(0, values.size() - 1)
        )
                + " y "
                + values.get(values.size() - 1);
    }

    private static String joinSentences(
            List<String> values) {

        return String.join(". ", values);
    }

    private static String safe(
            String value,
            String fallback) {

        return value == null
                || value.trim().isEmpty()
                || "null".equalsIgnoreCase(value.trim())
                ? fallback
                : value.trim();
    }
}
