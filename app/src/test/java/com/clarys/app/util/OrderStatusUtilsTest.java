package com.clarys.app.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.clarys.app.model.OrderRequest;

import org.junit.Test;

public class OrderStatusUtilsTest {

    @Test
    public void workflowAdvancesInExpectedOrder() {
        assertEquals(OrderRequest.STATUS_CONTACTED,
                OrderStatusUtils.nextStatus(OrderRequest.STATUS_PENDING));
        assertEquals(OrderRequest.STATUS_APPROVED,
                OrderStatusUtils.nextStatus(OrderRequest.STATUS_CONTACTED));
        assertEquals(OrderRequest.STATUS_PREPARING,
                OrderStatusUtils.nextStatus(OrderRequest.STATUS_APPROVED));
        assertEquals(OrderRequest.STATUS_COMPLETED,
                OrderStatusUtils.nextStatus(OrderRequest.STATUS_PREPARING));
    }

    @Test
    public void terminalStatusesAreNotActive() {
        assertTrue(OrderStatusUtils.isActive(OrderRequest.STATUS_PENDING));
        assertTrue(OrderStatusUtils.isActive(OrderRequest.STATUS_PREPARING));
        assertFalse(OrderStatusUtils.isActive(OrderRequest.STATUS_COMPLETED));
        assertFalse(OrderStatusUtils.isActive(OrderRequest.STATUS_REJECTED));
    }

    @Test
    public void labelsAreFriendlyForAdminAndClient() {
        assertEquals("NUEVO", OrderStatusUtils.label(OrderRequest.STATUS_PENDING));
        assertEquals("EN PREPARACIÓN", OrderStatusUtils.label(OrderRequest.STATUS_PREPARING));
        assertEquals("ENTREGADO", OrderStatusUtils.label(OrderRequest.STATUS_COMPLETED));
        assertEquals("CANCELADO", OrderStatusUtils.label(OrderRequest.STATUS_REJECTED));
    }

    @Test
    public void progressIndexMatchesWorkflowPosition() {
        assertEquals(0, OrderStatusUtils.progressIndex(OrderRequest.STATUS_PENDING));
        assertEquals(2, OrderStatusUtils.progressIndex(OrderRequest.STATUS_APPROVED));
        assertEquals(4, OrderStatusUtils.progressIndex(OrderRequest.STATUS_COMPLETED));
        assertEquals(-1, OrderStatusUtils.progressIndex(OrderRequest.STATUS_REJECTED));
    }
}
