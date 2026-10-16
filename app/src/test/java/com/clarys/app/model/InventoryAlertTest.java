package com.clarys.app.model;

import org.junit.Test;
import static org.junit.Assert.*;

public class InventoryAlertTest {
    @Test public void archivedProductDoesNotRaiseAnAlert() {
        Product product = new Product(1,"Archivado","","",0,1000,0,2,"","","",false,0);
        assertTrue(product.isOutOfStock());
        assertFalse(product.needsStockReplenishment());
        product.setActive(true);
        assertTrue(product.needsStockReplenishment());
    }

    @Test public void alertUsesAvailableStockAfterReservations() {
        Product product = new Product(1,"Activo","","",0,1000,5,2,"","","",true,0);
        assertFalse(product.needsStockReplenishment());
        product.setReservedStock(3);
        assertTrue(product.needsStockReplenishment());
        product.setActive(false);
        assertFalse(product.needsStockReplenishment());
    }
}
