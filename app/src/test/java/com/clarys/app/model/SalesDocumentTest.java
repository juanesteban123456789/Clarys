package com.clarys.app.model;

import org.junit.Test;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TimeZone;
import static org.junit.Assert.*;

public class SalesDocumentTest {
    private SalesDocument.Builder draft() {
        return new SalesDocument.Builder("taller-a", "Taller Clarys", "", "COP")
                .customer("María Núñez", "", "", "")
                .items(Collections.singletonList(new SalesDocument.Line("Camiseta · talla M", 2, 11000)));
    }
    @Test public void totalsAndDiscountUseLongWithoutChangingItems() {
        SalesDocument document = draft().discount(2000).build();
        assertEquals(22000, document.getSubtotal());
        assertEquals(20000, document.getTotal());
        assertEquals(11000, document.getItems().get(0).getUnitPrice());
    }
    @Test public void largeTotalsDoNotOverflowInteger() {
        SalesDocument document = draft().items(Collections.singletonList(new SalesDocument.Line("Lote", 100000, 100000000))).build();
        assertEquals(10000000000000L, document.getTotal());
    }
    @Test(expected = IllegalArgumentException.class) public void excessiveDiscountIsRejected() { draft().discount(22001).build(); }
    @Test(expected = IllegalArgumentException.class) public void emptyCustomerIsRejected() { draft().customer(" ", "", "", "").build(); }
    @Test(expected = IllegalArgumentException.class) public void emptyItemsAreRejected() { draft().items(Collections.emptyList()).build(); }
    @Test(expected = IllegalArgumentException.class) public void negativePricesAreRejected() { new SalesDocument.Line("Concepto", 1, -1); }
    @Test(expected = IllegalArgumentException.class) public void zeroQuantityIsRejected() { new SalesDocument.Line("Concepto", 0, 1); }
    @Test public void quotationNeverClaimsPayment() {
        SalesDocument document = draft().type(SalesDocument.Type.QUOTE).payment(SalesDocument.Payment.RECORDED).build();
        assertEquals(SalesDocument.Payment.UNVERIFIED, document.getPayment());
    }
    @Test public void snapshotIsIndependentFromEditableLines() {
        List<SalesDocument.Line> lines = new ArrayList<>(); lines.add(new SalesDocument.Line("Original", 2, 10));
        SalesDocument document = draft().items(lines).build();
        lines.clear();
        assertEquals(20, document.getTotal());
        assertEquals(1, document.getItems().size());
    }
    @Test(expected = UnsupportedOperationException.class) public void snapshotItemsCannotBeEdited() { draft().build().getItems().clear(); }
    @Test public void optionalNullValuesBecomeEmptyStrings() {
        SalesDocument document = draft().customer("Cliente", null, "null", null).notes(null).build();
        assertEquals("", document.getCustomerPhone()); assertEquals("", document.getCustomerId());
        assertEquals("", document.getCustomerEmail()); assertEquals("", document.getNotes());
    }
    @Test public void documentNumberDoesNotChangeWithDeviceTimeZone() {
        TimeZone original = TimeZone.getDefault();
        SalesDocument document = draft().identity("12345678-1234-1234-1234-123456789abc", 1792022400000L).build();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Auckland")); String first = document.getNumber();
            TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"));
            assertEquals(first, document.getNumber());
            assertTrue(first.startsWith("CP-"));
        } finally { TimeZone.setDefault(original); }
    }
}
