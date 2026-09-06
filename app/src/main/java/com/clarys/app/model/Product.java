package com.clarys.app.model;

import com.clarys.app.util.TextSanitizer;

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
    private String workshopId;
    private String workshopName;
    private String workshopWhatsapp;
    private String imageUrl;

    public Product(int id, String name, String description, String category, int purchasePrice,
            int salePrice, int stock, int minStock, String sizes, String colors, String sku,
            boolean active, int soldUnits) {
        this.id = id;
        this.name = TextSanitizer.orDefault(name, "Producto");
        this.description = TextSanitizer.emptyIfNull(description);
        this.category = TextSanitizer.orDefault(category, "General");
        this.purchasePrice = purchasePrice;
        this.salePrice = salePrice;
        this.stock = stock;
        this.minStock = minStock;
        this.sizes = TextSanitizer.emptyIfNull(sizes);
        this.colors = TextSanitizer.emptyIfNull(colors);
        this.sku = TextSanitizer.emptyIfNull(sku);
        this.active = active;
        this.soldUnits = soldUnits;
        this.workshopId = "";
        this.workshopName = "";
        this.workshopWhatsapp = "";
        this.imageUrl = "";
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = TextSanitizer.orDefault(name, "Producto");
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = TextSanitizer.emptyIfNull(description);
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = TextSanitizer.orDefault(category, "General");
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
        this.sizes = TextSanitizer.emptyIfNull(sizes);
    }

    public String getColors() {
        return colors;
    }

    public void setColors(String colors) {
        this.colors = TextSanitizer.emptyIfNull(colors);
    }

    public String getSku() {
        return sku;
    }

    /**
     * Nombre funcional del código SKU mostrado en la interfaz.
     */
    public String getInternalCode() {
        return sku;
    }

    public void setSku(String sku) {
        this.sku = TextSanitizer.emptyIfNull(sku);
    }

    public void setInternalCode(String internalCode) {
        this.sku = TextSanitizer.emptyIfNull(internalCode);
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

    public String getWorkshopId() {
        return workshopId;
    }

    public void setWorkshopId(String workshopId) {
        this.workshopId = TextSanitizer.emptyIfNull(workshopId);
    }

    public String getWorkshopName() {
        return workshopName;
    }

    public void setWorkshopName(String workshopName) {
        this.workshopName = TextSanitizer.emptyIfNull(workshopName);
    }

    public String getWorkshopWhatsapp() {
        return workshopWhatsapp;
    }

    public void setWorkshopWhatsapp(String workshopWhatsapp) {
        this.workshopWhatsapp = TextSanitizer.emptyIfNull(workshopWhatsapp);
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = TextSanitizer.emptyIfNull(imageUrl);
    }
}
