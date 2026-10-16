package com.clarys.app.model;

public class CartItem {
    private final Product product;
    private final boolean inventoryKnown;
    private int quantity;
    private String variantId = "";
    private String variantLabel = "";

    public CartItem(Product product, int quantity) {
        this(product, quantity, true);
    }

    public CartItem(Product product, int quantity, boolean inventoryKnown) {
        this.product = product;
        this.quantity = Math.max(1, quantity);
        this.inventoryKnown = inventoryKnown;
    }

    public Product getProduct() {
        return product;
    }

    public int getQuantity() {
        return quantity;
    }

    public boolean isInventoryKnown() {
        return inventoryKnown;
    }

    public String getVariantId() { return variantId; }
    public String getVariantLabel() { return variantLabel; }
    public void setVariant(String id, String label) {
        variantId = id == null ? "" : id;
        variantLabel = label == null ? "" : label;
    }
    public String getKey() { return product.getId() + ":" + variantId; }
    public int getAvailableStock() {
        if (variantId.isEmpty()) { return product.getStock(); }
        ProductVariant variant = product.getVariant(variantId);
        return variant == null ? 0 : variant.getAvailableStock();
    }

    public void setQuantity(int quantity) {
        this.quantity = Math.max(1, quantity);
    }

    public long getSubtotal() {
        return (long) product.getSalePrice() * quantity;
    }
}
