package com.clarys.app.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Agrupa los productos públicos que comparten una categoría dentro del catálogo.
 */
public final class CatalogSection {
    private final String category;
    private final List<Product> products;

    public CatalogSection(String category, List<Product> products) {
        this.category = category;
        this.products = new ArrayList<>(products);
    }

    public String getCategory() {
        return category;
    }

    public List<Product> getProducts() {
        return new ArrayList<>(products);
    }
}
