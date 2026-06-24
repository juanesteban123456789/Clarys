package com.clarys.app.model;

public class InventoryMovement {
    private final String productName;
    private final String type;
    private final int quantity;
    private final int previousStock;
    private final int newStock;
    private final String reason;

    public InventoryMovement(String productName, String type, int quantity, int previousStock,
            int newStock, String reason) {
        this.productName = productName;
        this.type = type;
        this.quantity = quantity;
        this.previousStock = previousStock;
        this.newStock = newStock;
        this.reason = reason;
    }

    public String getProductName() {
        return productName;
    }

    public String getType() {
        return type;
    }

    public int getQuantity() {
        return quantity;
    }

    public int getPreviousStock() {
        return previousStock;
    }

    public int getNewStock() {
        return newStock;
    }

    public String getReason() {
        return reason;
    }
}
