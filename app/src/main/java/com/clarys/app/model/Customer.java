package com.clarys.app.model;

import com.clarys.app.util.TextSanitizer;

public class Customer {
    private final String name;
    private final String phone;
    private int totalOrders;
    private long totalSpent;

    public Customer(String name, String phone, int totalOrders, long totalSpent) {
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

    public long getTotalSpent() {
        return totalSpent;
    }

    public void registerSale(long total) {
        totalOrders++;
        totalSpent += Math.max(0, total);
    }
}
