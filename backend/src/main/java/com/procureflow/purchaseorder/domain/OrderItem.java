package com.procureflow.purchaseorder.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * One snapshotted order line. {@code receivedQty} tracks goods receipt for
 * the invoice 3-way match; it never exceeds the ordered quantity.
 */
@Entity
@Table(name = "purchase_order_items")
public class OrderItem {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private PurchaseOrder order;

    @Column(nullable = false, length = 500)
    private String description;

    @Column(length = 200)
    private String category;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "unit_price_minor", nullable = false)
    private long unitPriceMinor;

    @Column(nullable = false, length = 3)
    private String currency = "MAD";

    @Column(name = "received_qty", nullable = false)
    private int receivedQty;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected OrderItem() {
        // JPA only
    }

    public OrderItem(
            PurchaseOrder order, String description, String category, int quantity, long unitPriceMinor,
            String currency) {
        this.order = order;
        this.description = description;
        this.category = category;
        this.quantity = quantity;
        this.unitPriceMinor = unitPriceMinor;
        this.currency = currency;
    }

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        createdAt = Instant.now();
    }

    public long lineTotalMinor() {
        return (long) quantity * unitPriceMinor;
    }

    public boolean isFullyReceived() {
        return receivedQty >= quantity;
    }

    public UUID getId() {
        return id;
    }

    public PurchaseOrder getOrder() {
        return order;
    }

    public String getDescription() {
        return description;
    }

    public String getCategory() {
        return category;
    }

    public int getQuantity() {
        return quantity;
    }

    public long getUnitPriceMinor() {
        return unitPriceMinor;
    }

    public String getCurrency() {
        return currency;
    }

    public int getReceivedQty() {
        return receivedQty;
    }

    public void receive(int qty) {
        this.receivedQty += qty;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
