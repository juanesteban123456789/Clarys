package com.clarys.app.util;

import com.clarys.app.model.OrderRequest;

/** Mantiene en un único lugar las etiquetas y transiciones del flujo de pedidos. */
public final class OrderStatusUtils {

    private static final String[] WORKFLOW_STATUSES = {
            OrderRequest.STATUS_PENDING,
            OrderRequest.STATUS_CONTACTED,
            OrderRequest.STATUS_APPROVED,
            OrderRequest.STATUS_PREPARING,
            OrderRequest.STATUS_COMPLETED
    };

    private OrderStatusUtils() {
    }

    public static String label(String status) {
        if (OrderRequest.STATUS_PENDING.equals(status)) {
            return "NUEVO";
        }
        if (OrderRequest.STATUS_CONTACTED.equals(status)) {
            return "CONTACTADO";
        }
        if (OrderRequest.STATUS_APPROVED.equals(status)) {
            return "CONFIRMADO";
        }
        if (OrderRequest.STATUS_PREPARING.equals(status)) {
            return "EN PREPARACIÓN";
        }
        if (OrderRequest.STATUS_COMPLETED.equals(status)) {
            return "ENTREGADO";
        }
        if (OrderRequest.STATUS_REJECTED.equals(status)) {
            return "CANCELADO";
        }
        return "PEDIDO";
    }

    public static boolean isActive(String status) {
        return OrderRequest.STATUS_PENDING.equals(status)
                || OrderRequest.STATUS_CONTACTED.equals(status)
                || OrderRequest.STATUS_APPROVED.equals(status)
                || OrderRequest.STATUS_PREPARING.equals(status);
    }

    public static String nextStatus(String status) {
        if (OrderRequest.STATUS_PENDING.equals(status)) {
            return OrderRequest.STATUS_CONTACTED;
        }
        if (OrderRequest.STATUS_CONTACTED.equals(status)) {
            return OrderRequest.STATUS_APPROVED;
        }
        if (OrderRequest.STATUS_APPROVED.equals(status)) {
            return OrderRequest.STATUS_PREPARING;
        }
        if (OrderRequest.STATUS_PREPARING.equals(status)) {
            return OrderRequest.STATUS_COMPLETED;
        }
        return null;
    }

    public static String nextActionLabel(String status) {
        if (OrderRequest.STATUS_PENDING.equals(status)) {
            return "Marcar como contactado";
        }
        if (OrderRequest.STATUS_CONTACTED.equals(status)) {
            return "Confirmar pedido";
        }
        if (OrderRequest.STATUS_APPROVED.equals(status)) {
            return "Iniciar preparación";
        }
        if (OrderRequest.STATUS_PREPARING.equals(status)) {
            return "Marcar como entregado";
        }
        return "Actualizar pedido";
    }

    public static String[] workflowStatuses() {
        return WORKFLOW_STATUSES.clone();
    }

    public static int progressIndex(String status) {
        for (int index = 0; index < WORKFLOW_STATUSES.length; index++) {
            if (WORKFLOW_STATUSES[index].equals(status)) {
                return index;
            }
        }
        return -1;
    }
}
