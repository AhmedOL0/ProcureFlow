package com.procureflow.approval.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * A bounded grant of decide power: the delegate may decide requests while the
 * window is open. Delegations are revocable (unlike decisions); expiry is
 * evaluated at decide time so no scheduler is needed for this slice.
 */
@Entity
@Table(name = "approval_delegations")
public class ApprovalDelegation {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "delegator_id", nullable = false)
    private UUID delegatorId;

    @Column(name = "delegate_id", nullable = false)
    private UUID delegateId;

    @Column(name = "starts_at", nullable = false, updatable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ApprovalDelegation() {
        // JPA only
    }

    public ApprovalDelegation(UUID tenantId, UUID delegatorId, UUID delegateId, Instant endsAt) {
        this.tenantId = tenantId;
        this.delegatorId = delegatorId;
        this.delegateId = delegateId;
        this.endsAt = endsAt;
    }

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        Instant now = Instant.now();
        startsAt = now;
        createdAt = now;
    }

    public boolean isActive(Instant now) {
        return !now.isBefore(startsAt) && now.isBefore(endsAt);
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getDelegatorId() {
        return delegatorId;
    }

    public UUID getDelegateId() {
        return delegateId;
    }

    public Instant getStartsAt() {
        return startsAt;
    }

    public Instant getEndsAt() {
        return endsAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
