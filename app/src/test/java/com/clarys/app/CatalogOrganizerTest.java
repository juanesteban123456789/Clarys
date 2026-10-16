package com.clarys.app;

import static org.junit.Assert.assertEquals;

import com.clarys.app.model.CatalogSection;
import com.clarys.app.model.Product;
import com.clarys.app.util.CatalogOrganizer;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class CatalogOrganizerTest {

    @Test
    public void groupByCategoryCreatesDynamicAlphabeticalSections() {
        Product jean = product(1, "Jean clásico", "Pantalones");
        Product polo = product(2, "Polo básico", "Camisetas");
        Product camiseta = product(3, "Camiseta deportiva", "Camisetas");

        List<CatalogSection> sections = CatalogOrganizer.groupByCategory(
                Arrays.asList(jean, polo, camiseta));

        assertEquals(2, sections.size());
        assertEquals("Camisetas", sections.get(0).getCategory());
        assertEquals(2, sections.get(0).getProducts().size());
        assertEquals("Camiseta deportiva", sections.get(0).getProducts().get(0).getName());
        assertEquals("Pantalones", sections.get(1).getCategory());
    }

    @Test
    public void groupByCategoryNormalizesLowercaseCategoryInitial() {
        List<CatalogSection> sections = CatalogOrganizer.groupByCategory(
                Arrays.asList(product(1, "Camiseta", "casual")));

        assertEquals("Casual", sections.get(0).getCategory());
        assertEquals("Ropa interior", CatalogOrganizer.formatCategoryLabel("ropa interior"));
    }

    private Product product(int id, String name, String category) {
        return new Product(
                id,
                name,
                "",
                category,
                10000,
                15000,
                5,
                1,
                "M",
                "Azul",
                "",
                true,
                0);
    }
}
