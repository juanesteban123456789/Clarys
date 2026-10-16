package com.clarys.app.model;

import com.clarys.app.util.TextSanitizer;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Fotografia inmutable de un documento comercial; no representa facturacion DIAN ni mueve stock. */
public final class SalesDocument {
    public static final String DISCLAIMER = "No constituye factura electrónica de venta.";
    public static final int MAX_ITEMS = 100;
    public enum Type {
        RECEIPT("Comprobante comercial", "CP"), QUOTE("Cotización", "CT");
        public final String label;
        public final String prefix;
        Type(String label, String prefix) { this.label = label; this.prefix = prefix; }
    }
    public enum Payment {
        UNVERIFIED("Pago no verificado"), RECORDED("Pago registrado por el taller");
        public final String label;
        Payment(String label) { this.label = label; }
    }

    private final String id, workshopId, businessName, businessPhone, currency;
    private final String customerName, customerPhone, customerId, customerEmail;
    private final String sourceKey, sourceLabel, notes;
    private final long createdAt, discount;
    private final Type type;
    private final Payment payment;
    private final List<Line> items;

    private SalesDocument(Builder builder) {
        id = UUID.fromString(builder.id).toString();
        workshopId = required(builder.workshopId, 100, "Taller");
        businessName = required(builder.businessName, 100, "Nombre del taller");
        businessPhone = optional(builder.businessPhone, 40);
        currency = required(builder.currency, 3, "Moneda").toUpperCase(Locale.ROOT);
        java.util.Currency.getInstance(currency);
        customerName = required(builder.customerName, 100, "Nombre del cliente");
        customerPhone = optional(builder.customerPhone, 40);
        customerId = optional(builder.customerId, 40);
        customerEmail = optional(builder.customerEmail, 120);
        sourceKey = optional(builder.sourceKey, 80);
        sourceLabel = optional(builder.sourceLabel, 100);
        notes = optional(builder.notes, 500);
        if (builder.createdAt <= 0 || builder.type == null || builder.payment == null) {
            throw new IllegalArgumentException("Fecha o tipo de documento inválido");
        }
        createdAt = builder.createdAt;
        type = builder.type;
        payment = type == Type.QUOTE ? Payment.UNVERIFIED : builder.payment;
        if (builder.items == null || builder.items.isEmpty() || builder.items.size() > MAX_ITEMS) {
            throw new IllegalArgumentException("Agrega entre 1 y 100 conceptos");
        }
        items = Collections.unmodifiableList(new ArrayList<>(builder.items));
        if (items.contains(null)) { throw new IllegalArgumentException("Concepto inválido"); }
        long subtotal = getSubtotal();
        if (builder.discount < 0 || builder.discount > subtotal) {
            throw new IllegalArgumentException("El descuento no puede superar el subtotal");
        }
        discount = builder.discount;
    }

    private static String required(String value, int max, String name) {
        String text = optional(value, max);
        if (text.isEmpty()) { throw new IllegalArgumentException(name + " es obligatorio"); }
        return text;
    }
    private static String optional(String value, int max) {
        String text = TextSanitizer.emptyIfNull(value).trim();
        if (text.length() > max) { throw new IllegalArgumentException("El texto supera " + max + " caracteres"); }
        return text;
    }
    public String getId() { return id; }
    public String getWorkshopId() { return workshopId; }
    public String getBusinessName() { return businessName; }
    public String getBusinessPhone() { return businessPhone; }
    public String getCurrency() { return currency; }
    public String getCustomerName() { return customerName; }
    public String getCustomerPhone() { return customerPhone; }
    public String getCustomerId() { return customerId; }
    public String getCustomerEmail() { return customerEmail; }
    public String getSourceKey() { return sourceKey; }
    public String getSourceLabel() { return sourceLabel; }
    public String getNotes() { return notes; }
    public long getCreatedAt() { return createdAt; }
    public long getDiscount() { return discount; }
    public Type getType() { return type; }
    public Payment getPayment() { return payment; }
    public List<Line> getItems() { return items; }
    public long getSubtotal() {
        long total = 0;
        for (Line line : items) { total = Math.addExact(total, line.getSubtotal()); }
        return total;
    }
    public long getTotal() { return getSubtotal() - discount; }
    public String getNumber() {
        SimpleDateFormat date = new SimpleDateFormat("yyyyMMdd", Locale.ROOT);
        date.setTimeZone(java.util.TimeZone.getTimeZone("America/Bogota"));
        return type.prefix + "-" + date.format(new Date(createdAt))
                + "-" + id.substring(0, 8).toUpperCase(Locale.ROOT);
    }

    public static final class Line {
        private final String description;
        private final int quantity;
        private final long unitPrice;
        public Line(String description, int quantity, long unitPrice) {
            this.description = required(description, 180, "Descripción");
            if (quantity < 1 || quantity > 100000 || unitPrice < 0 || unitPrice > 100000000L) {
                throw new IllegalArgumentException("Cantidad o precio fuera del rango permitido");
            }
            this.quantity = quantity;
            this.unitPrice = unitPrice;
        }
        public String getDescription() { return description; }
        public int getQuantity() { return quantity; }
        public long getUnitPrice() { return unitPrice; }
        public long getSubtotal() { return Math.multiplyExact(unitPrice, quantity); }
    }

    public static final class Builder {
        private String id = UUID.randomUUID().toString();
        private long createdAt = System.currentTimeMillis();
        private String workshopId, businessName, businessPhone, currency;
        private String customerName = "", customerPhone = "", customerId = "", customerEmail = "";
        private String sourceKey = "", sourceLabel = "Manual", notes = "";
        private long discount;
        private Type type = Type.RECEIPT;
        private Payment payment = Payment.UNVERIFIED;
        private List<Line> items = Collections.emptyList();
        public Builder(String workshopId, String businessName, String businessPhone, String currency) {
            this.workshopId = workshopId; this.businessName = businessName;
            this.businessPhone = businessPhone; this.currency = currency;
        }
        public Builder identity(String id, long createdAt) { this.id = id; this.createdAt = createdAt; return this; }
        public Builder customer(String name, String phone, String document, String email) {
            customerName = name; customerPhone = phone; customerId = document; customerEmail = email; return this;
        }
        public Builder type(Type value) { type = value; return this; }
        public Builder payment(Payment value) { payment = value; return this; }
        public Builder source(String key, String label) { sourceKey = key; sourceLabel = label; return this; }
        public Builder notes(String value) { notes = value; return this; }
        public Builder discount(long value) { discount = value; return this; }
        public Builder items(List<Line> value) { items = value; return this; }
        public SalesDocument build() { return new SalesDocument(this); }
    }
}
