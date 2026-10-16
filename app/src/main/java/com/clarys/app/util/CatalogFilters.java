package com.clarys.app.util;

import com.clarys.app.model.Product;
import com.clarys.app.model.ProductVariant;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class CatalogFilters {
    public String category = "";
    public String size = "";
    public String color = "";
    public int minimumPrice;
    public int maximumPrice = Integer.MAX_VALUE;
    public boolean availableOnly;
    public int sort;

    public List<Product> apply(List<Product> products, String query) {
        List<Product> result = new ArrayList<>();
        String text = fold(query);
        for (Product product : products) {
            if (!product.isActive() || product.getSalePrice() < minimumPrice
                    || product.getSalePrice() > maximumPrice || (availableOnly && product.getStock() == 0)
                    || (!category.isEmpty() && !fold(category).equals(fold(product.getCategory())))) { continue; }
            if (!text.isEmpty() && !fold(product.getName()+" "+product.getCategory()+" "
                    +product.getInternalCode()+" "+product.getWorkshopName()).contains(text)) { continue; }
            boolean matches = size.isEmpty() && color.isEmpty();
            if (!product.getVariants().isEmpty()) {
                for (ProductVariant variant : product.getVariants()) {
                    if ((size.isEmpty() || fold(size).equals(fold(variant.getSize())))
                            && (color.isEmpty() || fold(color).equals(fold(variant.getColor())))
                            && (!availableOnly || variant.getAvailableStock()>0)) { matches = true; }
                }
            } else {
                matches = containsChoice(product.getSizes(),size) && containsChoice(product.getColors(),color);
            }
            if (matches) { result.add(product); }
        }
        Comparator<Product> comparator = sort == 1 ? Comparator.comparingInt(Product::getSalePrice)
                : sort == 2 ? Comparator.comparingInt(Product::getSalePrice).reversed()
                : sort == 3 ? Comparator.comparingInt(Product::getId).reversed()
                : Comparator.comparing((Product product) -> fold(product.getName()));
        result.sort(comparator.thenComparingInt(Product::getId));
        return result;
    }

    private boolean containsChoice(String choices, String expected) {
        if (expected.isEmpty()) { return true; }
        for (String choice : choices.split(",")) {
            if (fold(choice).equals(fold(expected))) { return true; }
        }
        return false;
    }

    private static String fold(String text) {
        return Normalizer.normalize(text == null ? "" : text,Normalizer.Form.NFD)
                .replaceAll("\\p{M}","").trim().toLowerCase(Locale.ROOT);
    }
}
