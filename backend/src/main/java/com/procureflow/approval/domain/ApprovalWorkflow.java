package com.procureflow.approval.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * An approval lane: requests whose total falls in
 * [{@code minAmountMinor}, {@code maxAmountMinor}) follow its steps.
 * A null max means unbounded. Only active workflows attach to new requests.
 */
@Entity
@Table(name = "approval_workflows")
public class ApprovalWorkflow {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "min_amount_minor", nullable = false)
    private long minAmountMinor;

    @Column(name = "max_amount_minor")
    private Long maxAmountMinor;

    @Column(name = "escalate_after_days", nullable = false)
    private int escalateAfterDays = 3;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ApprovalWorkflow() {
        // JPA only
    }

    public ApprovalWorkflow(UUID tenantId, String name, long minAmountMinor, Long maxAmountMinor, int escalateAfterDays) {
        this.tenantId = tenantId;
        this.name = name;
        this.minAmountMinor = minAmountMinor;
        this.maxAmountMinor = maxAmountMinor;
        this.escalateAfterDays = escalateAfterDays;
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

    public boolean matches(long totalMinor) {
        return totalMinor >= minAmountMinor && (maxAmountMinor == null || totalMinor < maxAmountMinor);
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public long getMinAmountMinor() {
        return minAmountMinor;
    }

    public Long getMaxAmountMinor() {
        return maxAmountMinor;
    }

    public int getEscalateAfterDays() {
        return escalateAfterDays;
    }

    public void setEscalateAfterDays(int escalateAfterDays) {
        this.escalateAfterDays = escalateAfterDays;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
