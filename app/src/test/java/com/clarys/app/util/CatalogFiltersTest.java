package com.clarys.app.util;

import com.clarys.app.model.Product;
import com.clarys.app.model.ProductVariant;
import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import static org.junit.Assert.*;

public class CatalogFiltersTest {
    private Product product() {
        Product product=new Product(1,"Pantalón","", "Pantalones",0,10000,4,1,"M,L","Negro,Azul","A1",true,0);
        product.setVariants(Arrays.asList(new ProductVariant("m","M","Negro",2,2),new ProductVariant("l","L","Azul",2,0)));
        product.setReservedStock(2); return product;
    }
    @Test public void sizeAndColorMustMatchSameVariant() {
        CatalogFilters filters=new CatalogFilters(); filters.size="M"; filters.color="Azul";
        assertTrue(filters.apply(Collections.singletonList(product()),"").isEmpty());
    }
    @Test public void fullyReservedVariantIsNotAvailable() {
        CatalogFilters filters=new CatalogFilters(); filters.size="M"; filters.availableOnly=true;
        assertTrue(filters.apply(Collections.singletonList(product()),"").isEmpty());
        filters.size="L";
        assertEquals(1,filters.apply(Collections.singletonList(product()),"pantalon").size());
    }
    @Test public void priceBoundsExcludeNonMatchingProduct() {
        CatalogFilters filters=new CatalogFilters(); filters.minimumPrice=10001;
        assertTrue(filters.apply(Collections.singletonList(product()),"").isEmpty());
    }
}
