package com.clarys.app.model;

import java.util.ArrayList;
import java.util.List;

public class OrderRequest {

    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_APPROVED = "approved";
    public static final String STATUS_REJECTED = "rejected";
    public static final String STATUS_COMPLETED = "completed";

    private final int id;
    private final String workshopId;
    private final String customerName;
    private final String customerPhone;
    private final List<CartItem> items;

    private String status;
    private String aiDescription;
    private String adminNotes;
    private String receiptUrl;
    private String createdAt;
    private String updatedAt;
    private String approvedAt;
    private String rejectedAt;
    private String completedAt;

    public OrderRequest(
            int id,
            String workshopId,
            String customerName,
            String customerPhone,
            List<CartItem> items,
            String status,
            String aiDescription,
            String adminNotes,
            String receiptUrl,
            String createdAt,
            String updatedAt,
            String approvedAt,
            String rejectedAt,
            String completedAt
    ) {
        this.id = id;
        this.workshopId = workshopId;
        this.customerName = customerName;
        this.customerPhone = customerPhone;
        this.items = items == null
                ? new ArrayList<>()
                : new ArrayList<>(items);
        this.status = status;
        this.aiDescription = aiDescription;
        this.adminNotes = adminNotes;
        this.receiptUrl = receiptUrl;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.approvedAt = approvedAt;
        this.rejectedAt = rejectedAt;
        this.completedAt = completedAt;
    }

    public int getId() {
        return id;
    }

    public String getWorkshopId() {
        return workshopId;
    }

    public String getCustomerName() {
        return customerName;
    }

    public String getCustomerPhone() {
        return customerPhone;
    }

    public List<CartItem> getItems() {
        return new ArrayList<>(items);
    }

    public String getStatus() {
        return status;
    }

    public String getAdminNotes() {
        return adminNotes;
    }

    public String getAiDescription() {
        return aiDescription;
    }

    public String getReceiptUrl() {
        return receiptUrl;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public String getApprovedAt() {
        return approvedAt;
    }

    public String getRejectedAt() {
        return rejectedAt;
    }

    public String getCompletedAt() {
        return completedAt;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public void setAdminNotes(String adminNotes) {
        this.adminNotes = adminNotes;
    }

    public void setAiDescription(String aiDescription) {
        this.aiDescription = aiDescription;
    }

    public void setReceiptUrl(String receiptUrl) {
        this.receiptUrl = receiptUrl;
    }

    public int getTotal() {
        int total = 0;

        for (CartItem item : items) {
            total += item.getSubtotal();
        }

        return total;
    }

    public int getTotalUnits() {
        int total = 0;

        for (CartItem item : items) {
            total += item.getQuantity();
        }

        return total;
    }

    public boolean isPending() {
        return STATUS_PENDING.equalsIgnoreCase(status);
    }

    public boolean isApproved() {
        return STATUS_APPROVED.equalsIgnoreCase(status);
    }

    public boolean isRejected() {
        return STATUS_REJECTED.equalsIgnoreCase(status);
    }

    public boolean isCompleted() {
        return STATUS_COMPLETED.equalsIgnoreCase(status);
    }
}
