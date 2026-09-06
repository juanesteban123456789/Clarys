package com.clarys.app.model;

import com.clarys.app.util.TextSanitizer;

public class Customer {
    private final String name;
    private final String phone;
    private int totalOrders;
    private int totalSpent;

    public Customer(String name, String phone, int totalOrders, int totalSpent) {
        this.name = TextSanitizer.orDefault(name, "Cliente");
        this.phone = TextSanitizer.emptyIfNull(phone);
        this.totalOrders = totalOrders;
        this.totalSpent = totalSpent;
    }

    public String getName() {
        return name;
    }

    public String getPhone() {
        return phone;
    }

    public int getTotalOrders() {
        return totalOrders;
    }

    public int getTotalSpent() {
        return totalSpent;
    }

    public void registerSale(int total) {
        totalOrders++;
        totalSpent += Math.max(0, total);
    }
}
