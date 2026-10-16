package com.clarys.app.util;

import com.clarys.app.model.CatalogSection;
import com.clarys.app.model.Product;

import java.text.Collator;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Organiza los productos del catálogo en secciones dinámicas por categoría.
 */
public final class CatalogOrganizer {
    private static final String DEFAULT_CATEGORY = "Otros";

    private CatalogOrganizer() {
    }

    public static List<CatalogSection> groupByCategory(List<Product> products) {
        Collator spanishCollator = Collator.getInstance(new Locale("es", "CO"));
        spanishCollator.setStrength(Collator.PRIMARY);

        Map<String, List<Product>> groupedProducts = new TreeMap<>(spanishCollator);
        Map<String, String> displayNames = new LinkedHashMap<>();

        if (products != null) {
            for (Product product : products) {
                if (product == null) {
                    continue;
                }

            String category = formatCategoryLabel(product.getCategory());
                String categoryKey = category.toLowerCase(new Locale("es", "CO"));
                displayNames.putIfAbsent(categoryKey, category);
                groupedProducts.computeIfAbsent(categoryKey, ignored -> new ArrayList<>())
                        .add(product);
            }
        }

        List<CatalogSection> sections = new ArrayList<>();
        for (Map.Entry<String, List<Product>> entry : groupedProducts.entrySet()) {
            entry.getValue().sort((first, second) ->
                    spanishCollator.compare(first.getName(), second.getName()));
            sections.add(new CatalogSection(displayNames.get(entry.getKey()), entry.getValue()));
        }
        return sections;
    }

    public static String formatCategoryLabel(String category) {
        if (category == null || category.trim().isEmpty()) {
            return DEFAULT_CATEGORY;
        }
        String trimmedCategory = category.trim();
        return trimmedCategory.substring(0, 1).toUpperCase(Locale.ROOT)
                + trimmedCategory.substring(1);
    }
}
