package com.clarys.app.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Sale {
    private final int id;
    private final String customerName;
    private final String customerPhone;
    private final String paymentMethod;
    private final String status;
    private final int discount;
    private final List<CartItem> items;

    public Sale(int id, String customerName, String customerPhone, String paymentMethod,
            String status, int discount, List<CartItem> items) {
        this.id = id;
        this.customerName = customerName;
        this.customerPhone = customerPhone;
        this.paymentMethod = paymentMethod;
        this.status = status;
        this.discount = Math.max(0, discount);
        this.items = new ArrayList<>(items);
    }

    public int getId() {
        return id;
    }

    public String getCustomerName() {
        return customerName;
    }

    public String getCustomerPhone() {
        return customerPhone;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public String getStatus() {
        return status;
    }

    public int getDiscount() {
        return discount;
    }

    public List<CartItem> getItems() {
        return Collections.unmodifiableList(items);
    }

    public int getSubtotal() {
        int subtotal = 0;
        for (CartItem item : items) {
            subtotal += item.getSubtotal();
        }
        return subtotal;
    }

    public int getTotal() {
        return Math.max(0, getSubtotal() - discount);
    }

    public int getTotalUnits() {
        int units = 0;
        for (CartItem item : items) {
            units += item.getQuantity();
        }
        return units;
    }
}
