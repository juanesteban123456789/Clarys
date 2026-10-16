package com.clarys.app.model;

import com.clarys.app.util.TextSanitizer;

/** Datos publicos de una pieza promocional; nunca incluye costos ni datos de clientes. */
public final class ProductPromotion {
    public enum Format {
        POST(1080, 1350), STORY(1080, 1920);
        public final int width;
        public final int height;
        Format(int width, int height) { this.width = width; this.height = height; }
    }

    private final String name, category, workshop, price, phone, sizes, colors;

    public ProductPromotion(Product product, String workshop, String formattedPrice, String phone) {
        if (product == null) { throw new IllegalArgumentException("Producto requerido"); }
        name = product.getName();
        category = product.getCategory();
        this.workshop = TextSanitizer.orDefault(workshop, "Clarys");
        price = TextSanitizer.emptyIfNull(formattedPrice);
        this.phone = TextSanitizer.emptyIfNull(phone);
        sizes = product.getSizes();
        colors = product.getColors();
    }

    public String getName() { return name; }
    public String getCategory() { return category; }
    public String getWorkshop() { return workshop; }
    public String getPrice() { return price; }
    public String getPhone() { return phone; }

    /** Texto editable, sin prometer stock ni enlaces a un catalogo web inexistente. */
    public String caption(boolean showPrice, boolean showContact) {
        StringBuilder result = new StringBuilder(name).append('\n').append(workshop);
        if (showPrice && !price.isEmpty()) { result.append("\nPrecio: ").append(price); }
        if (!sizes.isEmpty()) { result.append("\nTallas: ").append(sizes); }
        if (!colors.isEmpty()) { result.append("\nColores: ").append(colors); }
        if (showContact && !phone.isEmpty()) { result.append("\nPedidos por WhatsApp: ").append(phone); }
        result.append("\nConsulta disponibilidad antes de realizar tu pedido.");
        return result.length() <= 2200 ? result.toString() : result.substring(0, 2197) + "...";
    }
}
