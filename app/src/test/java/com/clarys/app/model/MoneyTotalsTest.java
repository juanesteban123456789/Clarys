package com.clarys.app.model;

import org.junit.Test;
import java.util.Collections;
import static org.junit.Assert.assertEquals;

public class MoneyTotalsTest {
    @Test public void largeOrdersDoNotOverflowSignedInteger() {
        Product product=new Product(1,"Prenda","", "",0,1000000000,1000,0,"","","",true,0);
        CartItem item=new CartItem(product,1000);
        assertEquals(1000000000000L,item.getSubtotal());
        Sale sale=new Sale(1,"Cliente","","","",0,Collections.singletonList(item));
        assertEquals(1000000000000L,sale.getTotal());
    }
}
