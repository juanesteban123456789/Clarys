package com.clarys.app.model;

import com.clarys.app.util.TextSanitizer;

/** Existencias de una combinación concreta de talla y color. */
public final class ProductVariant {
    private final String id;
    private final String size;
    private final String color;
    private final int stock;
    private final int reserved;

    public ProductVariant(String id, String size, String color, int stock, int reserved) {
        this.id = TextSanitizer.emptyIfNull(id);
        this.size = TextSanitizer.emptyIfNull(size);
        this.color = TextSanitizer.emptyIfNull(color);
        this.stock = Math.max(0, stock);
        this.reserved = Math.max(0, reserved);
    }

    public String getId() { return id; }
    public String getSize() { return size; }
    public String getColor() { return color; }
    public int getStock() { return stock; }
    public int getReservedStock() { return reserved; }
    public int getAvailableStock() { return Math.max(0, stock - reserved); }
    public String getLabel() { return size + " · " + color; }
}
