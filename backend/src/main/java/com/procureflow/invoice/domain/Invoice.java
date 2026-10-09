package com.procureflow.invoice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;

/**
 * A supplier invoice against one order. Numbers are unique per tenant.
 * Status derives from accumulated payments: none → UNPAID, some → PARTIAL,
 * covered → PAID.
 */
@Entity
@Table(name = "invoices", uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_id", "number"}))
public class Invoice {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(nullable = false, length = 50)
    private String number;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.UNPAID;

    @Column(name = "total_minor", nullable = false)
    private long totalMinor;

    @Column(nullable = false, length = 3)
    private String currency = "MAD";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Invoice() {
        // JPA only
    }

    public Invoice(UUID tenantId, UUID orderId, String number, long totalMinor, String currency) {
        this.tenantId = tenantId;
        this.orderId = orderId;
        this.number = number;
        this.totalMinor = totalMinor;
        this.currency = currency;
    }

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public void applyPayments(long paidMinor) {
        if (paidMinor <= 0) {
            this.status = Status.UNPAID;
        } else if (paidMinor >= totalMinor) {
            this.status = Status.PAID;
        } else {
            this.status = Status.PARTIAL;
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public String getNumber() {
        return number;
    }

    public Status getStatus() {
        return status;
    }

    public long getTotalMinor() {
        return totalMinor;
    }

    public String getCurrency() {
        return currency;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public enum Status {
        UNPAID,
        PARTIAL,
        PAID
    }
}
