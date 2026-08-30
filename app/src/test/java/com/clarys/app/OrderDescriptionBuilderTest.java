package com.clarys.app;

import static org.junit.Assert.assertTrue;

import com.clarys.app.model.CartItem;
import com.clarys.app.model.OrderRequest;
import com.clarys.app.model.Product;

import org.junit.Test;

import java.util.Arrays;

public class OrderDescriptionBuilderTest {

    @Test
    public void buildIncludesCustomerProductsTotalAndStockReminder() {
        Product shirt = product(
                1,
                "Camiseta",
                25000
        );
        Product pants = product(
                2,
                "Pantalón",
                60000
        );

        OrderRequest order = new OrderRequest(
                10,
                "workshop",
                "Juanito",
                "573001234567",
                Arrays.asList(
                        new CartItem(shirt, 2),
                        new CartItem(pants, 1)
                ),
                OrderRequest.STATUS_PENDING,
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                ""
        );

        String description = OrderDescriptionBuilder.build(
                order,
                "$ 110.000"
        );

        assertTrue(description.contains("Juanito"));
        assertTrue(description.contains("2 unidades de Camiseta"));
        assertTrue(description.contains("1 unidad de Pantalón"));
        assertTrue(description.contains("$ 110.000"));
        assertTrue(description.contains("stock suficiente"));
    }

    private Product product(
            int id,
            String name,
            int price) {

        return new Product(
                id,
                name,
                "",
                "Ropa",
                0,
                price,
                10,
                2,
                "",
                "",
                "",
                true,
                0
        );
    }
}
