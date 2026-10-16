package com.clarys.app.model;

import com.clarys.app.util.TextSanitizer;

import java.util.ArrayList;
import java.util.List;

public class OrderRequest {

    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_CONTACTED = "contacted";
    public static final String STATUS_APPROVED = "approved";
    public static final String STATUS_PREPARING = "preparing";
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
    private List<OrderEvent> events = new ArrayList<>();
    private int saleId;

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
        this.workshopId = TextSanitizer.emptyIfNull(workshopId);
        this.customerName = TextSanitizer.orDefault(customerName, "Cliente");
        this.customerPhone = TextSanitizer.emptyIfNull(customerPhone);
        this.items = items == null
                ? new ArrayList<>()
                : new ArrayList<>(items);
        this.status = TextSanitizer.orDefault(status, STATUS_PENDING);
        this.aiDescription = TextSanitizer.emptyIfNull(aiDescription);
        this.adminNotes = TextSanitizer.emptyIfNull(adminNotes);
        this.receiptUrl = TextSanitizer.emptyIfNull(receiptUrl);
        this.createdAt = TextSanitizer.emptyIfNull(createdAt);
        this.updatedAt = TextSanitizer.emptyIfNull(updatedAt);
        this.approvedAt = TextSanitizer.emptyIfNull(approvedAt);
        this.rejectedAt = TextSanitizer.emptyIfNull(rejectedAt);
        this.completedAt = TextSanitizer.emptyIfNull(completedAt);
    }

    public int getId() {
        return id;
    }

    public List<OrderEvent> getEvents() { return new ArrayList<>(events); }
    public void setEvents(List<OrderEvent> values) { events = new ArrayList<>(values); }
    public int getSaleId() { return saleId; }
    public void setSaleId(int value) { saleId=value; }

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
        this.status = TextSanitizer.orDefault(status, STATUS_PENDING);
    }

    public void setAdminNotes(String adminNotes) {
        this.adminNotes = TextSanitizer.emptyIfNull(adminNotes);
    }

    public void setAiDescription(String aiDescription) {
        this.aiDescription = TextSanitizer.emptyIfNull(aiDescription);
    }

    public void setReceiptUrl(String receiptUrl) {
        this.receiptUrl = TextSanitizer.emptyIfNull(receiptUrl);
    }

    public long getTotal() {
        long total = 0;

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

    public boolean isContacted() {
        return STATUS_CONTACTED.equalsIgnoreCase(status);
    }

    public boolean isPreparing() {
        return STATUS_PREPARING.equalsIgnoreCase(status);
    }

    public boolean isRejected() {
        return STATUS_REJECTED.equalsIgnoreCase(status);
    }

    public boolean isCompleted() {
        return STATUS_COMPLETED.equalsIgnoreCase(status);
    }
}
