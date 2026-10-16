package com.clarys.app.model;

import org.junit.Test;
import static org.junit.Assert.*;

public class ProductPromotionTest {
    private Product product() {
        return new Product(7, "Camiseta de algodón", "", "Camisetas", 5000, 12000, 3,
                1, "S, M, L", "Azul", "TEST", true, 0);
    }
    @Test public void includesOnlyPublicProductData() {
        String caption = new ProductPromotion(product(), "Taller Clarys", "$ 12.000 COP", "+573001234567").caption(true, true);
        assertTrue(caption.contains("algodón"));
        assertTrue(caption.contains("$ 12.000 COP"));
        assertTrue(caption.contains("+573001234567"));
        assertTrue(caption.contains("S, M, L"));
        assertFalse(caption.contains("5000"));
        assertFalse(caption.contains("https://"));
    }
    @Test public void priceAndContactAreOptional() {
        String caption = new ProductPromotion(product(), "Clarys", "$ 12.000", "3001234567").caption(false, false);
        assertFalse(caption.contains("12.000"));
        assertFalse(caption.contains("3001234567"));
        assertTrue(caption.contains("Consulta disponibilidad"));
    }
    @Test public void missingContactIsNeverLiteralNull() {
        String caption = new ProductPromotion(product(), "null", "$ 12.000", null).caption(true, true);
        assertFalse(caption.contains("null"));
        assertFalse(caption.contains("WhatsApp"));
    }
    @Test public void snapshotDoesNotChangeWhenProductChanges() {
        Product product = product();
        ProductPromotion snapshot = new ProductPromotion(product, "Clarys", "$ 12.000", "");
        product.setName("Otro producto");
        assertEquals("Camiseta de algodón", snapshot.getName());
    }
    @Test public void capsDescriptionLength() {
        Product product = product();
        product.setColors(new String(new char[4000]).replace('\0', 'A'));
        assertTrue(new ProductPromotion(product, "Clarys", "", "").caption(false, false).length() <= 2200);
    }
    @Test public void formatsHaveStableDimensions() {
        assertEquals(1080, ProductPromotion.Format.POST.width);
        assertEquals(1350, ProductPromotion.Format.POST.height);
        assertEquals(1920, ProductPromotion.Format.STORY.height);
    }
    @Test(expected = IllegalArgumentException.class) public void rejectsMissingProduct() {
        new ProductPromotion(null, "Clarys", "", "");
    }
}
