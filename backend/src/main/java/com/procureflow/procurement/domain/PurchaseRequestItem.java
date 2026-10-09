package com.procureflow.procurement.domain;

import com.procureflow.supplier.domain.Supplier;
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
 * One line of a purchase request. Money travels as integer minor units with
 * an ISO currency code (see docs/api/README.md); an optional supplier link
 * records the preferred source when the requester already knows it.
 */
@Entity
@Table(name = "purchase_request_items")
public class PurchaseRequestItem {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "request_id", nullable = false)
    private PurchaseRequest request;

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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id")
    private Supplier supplier;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected PurchaseRequestItem() {
        // JPA only
    }

    public PurchaseRequestItem(PurchaseRequest request, String description, int quantity, long unitPriceMinor) {
        this.request = request;
        this.description = description;
        this.quantity = quantity;
        this.unitPriceMinor = unitPriceMinor;
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

    public PurchaseRequest getRequest() {
        return request;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public long getUnitPriceMinor() {
        return unitPriceMinor;
    }

    public void setUnitPriceMinor(long unitPriceMinor) {
        this.unitPriceMinor = unitPriceMinor;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public Supplier getSupplier() {
        return supplier;
    }

    public void setSupplier(Supplier supplier) {
        this.supplier = supplier;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
