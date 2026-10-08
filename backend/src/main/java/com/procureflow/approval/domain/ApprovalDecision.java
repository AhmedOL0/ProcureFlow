package com.procureflow.approval.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;

/**
 * An immutable approval verdict on one purchase request. Exactly one row per
 * request (unique constraint); there is no update or delete path. The request
 * itself lives in procurement and is referenced by id only — modules never
 * share entities.
 */
@Entity
@Table(name = "approval_decisions", uniqueConstraints = @UniqueConstraint(columnNames = {"request_id"}))
public class ApprovalDecision {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "request_id", nullable = false)
    private UUID requestId;

    @Column(name = "decider_id", nullable = false)
    private UUID deciderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Decision decision;

    @Column(columnDefinition = "TEXT")
    private String comment;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ApprovalDecision() {
        // JPA only
    }

    public ApprovalDecision(UUID tenantId, UUID requestId, UUID deciderId, Decision decision, String comment) {
        this.tenantId = tenantId;
        this.requestId = requestId;
        this.deciderId = deciderId;
        this.decision = decision;
        this.comment = comment;
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

    public UUID getRequestId() {
        return requestId;
    }

    public UUID getDeciderId() {
        return deciderId;
    }

    public Decision getDecision() {
        return decision;
    }

    public String getComment() {
        return comment;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public enum Decision {
        APPROVED,
        REJECTED
    }
}
