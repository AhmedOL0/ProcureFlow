package com.procureflow.invoice.domain;

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
 * One invoiced order line. The order line lives in the purchaseorder module
 * and is referenced by id only.
 */
@Entity
@Table(name = "invoice_lines")
public class InvoiceLine {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id", nullable = false)
    private Invoice invoice;

    @Column(name = "order_item_id", nullable = false)
    private UUID orderItemId;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "unit_price_minor", nullable = false)
    private long unitPriceMinor;

    @Column(nullable = false, length = 3)
    private String currency = "MAD";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected InvoiceLine() {
        // JPA only
    }

    public InvoiceLine(Invoice invoice, UUID orderItemId, int quantity, long unitPriceMinor, String currency) {
        this.invoice = invoice;
        this.orderItemId = orderItemId;
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

    public UUID getId() {
        return id;
    }

    public Invoice getInvoice() {
        return invoice;
    }

    public UUID getOrderItemId() {
        return orderItemId;
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

    public Instant getCreatedAt() {
        return createdAt;
    }
}
