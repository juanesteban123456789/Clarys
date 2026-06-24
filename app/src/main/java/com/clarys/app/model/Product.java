package com.clarys.app.model;

public class Product {
    private final int id;
    private String name;
    private String description;
    private String category;
    private int purchasePrice;
    private int salePrice;
    private int stock;
    private int minStock;
    private String sizes;
    private String colors;
    private String sku;
    private boolean active;
    private int soldUnits;

    public Product(int id, String name, String description, String category, int purchasePrice,
            int salePrice, int stock, int minStock, String sizes, String colors, String sku,
            boolean active, int soldUnits) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.category = category;
        this.purchasePrice = purchasePrice;
        this.salePrice = salePrice;
        this.stock = stock;
        this.minStock = minStock;
        this.sizes = sizes;
        this.colors = colors;
        this.sku = sku;
        this.active = active;
        this.soldUnits = soldUnits;
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public int getPurchasePrice() {
        return purchasePrice;
    }

    public void setPurchasePrice(int purchasePrice) {
        this.purchasePrice = purchasePrice;
    }

    public int getSalePrice() {
        return salePrice;
    }

    public void setSalePrice(int salePrice) {
        this.salePrice = salePrice;
    }

    public int getStock() {
        return stock;
    }

    public void setStock(int stock) {
        this.stock = Math.max(0, stock);
    }

    public int getMinStock() {
        return minStock;
    }

    public void setMinStock(int minStock) {
        this.minStock = Math.max(0, minStock);
    }

    public String getSizes() {
        return sizes;
    }

    public void setSizes(String sizes) {
        this.sizes = sizes;
    }

    public String getColors() {
        return colors;
    }

    public void setColors(String colors) {
        this.colors = colors;
    }

    public String getSku() {
        return sku;
    }

    public void setSku(String sku) {
        this.sku = sku;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public int getSoldUnits() {
        return soldUnits;
    }

    public void addSoldUnits(int quantity) {
        soldUnits += Math.max(0, quantity);
    }

    public boolean isLowStock() {
        return stock > 0 && stock <= minStock;
    }

    public boolean isOutOfStock() {
        return stock == 0;
    }

    public int getPotentialProfit() {
        return Math.max(0, salePrice - purchasePrice);
    }
}
