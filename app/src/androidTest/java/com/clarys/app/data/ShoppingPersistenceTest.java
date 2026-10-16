package com.clarys.app.data;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.clarys.app.model.CartItem;
import com.clarys.app.model.Product;
import com.clarys.app.model.ProductVariant;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.Collections;
import static org.junit.Assert.*;

/** Usa preferencias propias de prueba; no altera cuentas, carrito ni pedidos reales. */
@RunWith(AndroidJUnit4.class)
public class ShoppingPersistenceTest {
    private Context isolated() {
        return new ContextWrapper(InstrumentationRegistry.getInstrumentation().getTargetContext()) {
            @Override public Context getApplicationContext() { return this; }
            @Override public SharedPreferences getSharedPreferences(String name,int mode) {
                return super.getSharedPreferences("verification_"+name,mode);
            }
        };
    }
    @Test public void cartSurvivesNewRepositoryInstanceAndKeepsVariant() throws Exception {
        Context context=isolated();
        ShoppingPreferences preferences=new ShoppingPreferences(context);
        Product product=new Product(910001,"Producto de prueba","", "",0,12000,4,0,"M","Negro","",true,0);
        product.setWorkshopId("workshop-test");
        product.setVariants(Collections.singletonList(new ProductVariant("m-negro","M","Negro",4,1)));
        product.setReservedStock(1);
        CartItem item=new CartItem(product,2); item.setVariant("m-negro","M · Negro");
        preferences.saveCart(Collections.singletonList(item));
        CartItem restored=new ShoppingPreferences(context).loadCart().get(0);
        assertEquals("m-negro",restored.getVariantId());
        assertEquals(2,restored.getQuantity());
        assertEquals(24000,restored.getSubtotal());
        assertEquals(3,restored.getAvailableStock());
        String key=preferences.submissionKey("same-order");
        assertEquals(key,new ShoppingPreferences(context).submissionKey("same-order"));
        assertNotEquals(key,preferences.submissionKey("different-order"));
        preferences.saveCart(Collections.emptyList());
        assertTrue(new ShoppingPreferences(context).loadCart().isEmpty());
    }
    @Test public void favoritesRemainOnThisDevice() {
        ShoppingPreferences preferences=new ShoppingPreferences(isolated());
        if(preferences.isFavorite(910002)) { preferences.toggleFavorite(910002); }
        assertTrue(preferences.toggleFavorite(910002));
        assertTrue(new ShoppingPreferences(isolated()).isFavorite(910002));
        assertFalse(preferences.toggleFavorite(910002));
    }
}
