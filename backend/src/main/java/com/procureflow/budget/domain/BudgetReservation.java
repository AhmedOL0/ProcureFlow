package com.procureflow.budget.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;

/**
 * An approved request's hold on a budget pot. Exactly one row per request;
 * released (deleted) when an approved request is cancelled. Rejections never
 * create one — only approvals move money.
 */
@Entity
@Table(name = "budget_reservations", uniqueConstraints = @UniqueConstraint(columnNames = {"request_id"}))
public class BudgetReservation {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "budget_id", nullable = false)
    private UUID budgetId;

    @Column(name = "request_id", nullable = false)
    private UUID requestId;

    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected BudgetReservation() {
        // JPA only
    }

    public BudgetReservation(UUID tenantId, UUID budgetId, UUID requestId, long amountMinor) {
        this.tenantId = tenantId;
        this.budgetId = budgetId;
        this.requestId = requestId;
        this.amountMinor = amountMinor;
    }

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getBudgetId() {
        return budgetId;
    }

    public UUID getRequestId() {
        return requestId;
    }

    public long getAmountMinor() {
        return amountMinor;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
