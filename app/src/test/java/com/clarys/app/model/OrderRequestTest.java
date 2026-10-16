package com.clarys.app.model;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Collections;

public class OrderRequestTest {

    @Test
    public void optionalTextsNeverExposeNullLiteral() {
        OrderRequest request = new OrderRequest(
                20,
                "workshop",
                "Juanes",
                "3046332807",
                Collections.emptyList(),
                OrderRequest.STATUS_PENDING,
                "null",
                null,
                "null",
                "",
                "",
                null,
                "null",
                null
        );

        assertEquals("", request.getAiDescription());
        assertEquals("", request.getAdminNotes());
        assertEquals("", request.getReceiptUrl());
        assertEquals("", request.getApprovedAt());
        assertEquals("", request.getRejectedAt());
        assertEquals("", request.getCompletedAt());
    }
}
