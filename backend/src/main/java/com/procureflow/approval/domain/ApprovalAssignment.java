package com.procureflow.approval.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;

/**
 * A request pinned to its workflow's current step. At most one row per
 * request; overdue steps escalate to the next level (flagged, with a fresh
 * due date). A request whose status leaves SUBMITTED keeps its row as
 * history — decisions are the source of truth for the outcome.
 */
@Entity
@Table(name = "approval_assignments", uniqueConstraints = @UniqueConstraint(columnNames = {"request_id"}))
public class ApprovalAssignment {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "request_id", nullable = false)
    private UUID requestId;

    @Column(name = "workflow_id", nullable = false)
    private UUID workflowId;

    @Column(name = "current_step_order", nullable = false)
    private int currentStepOrder;

    @Column(name = "due_at", nullable = false)
    private Instant dueAt;

    @Column(nullable = false)
    private boolean escalated;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ApprovalAssignment() {
        // JPA only
    }

    public ApprovalAssignment(UUID tenantId, UUID requestId, UUID workflowId, int currentStepOrder, Instant dueAt) {
        this.tenantId = tenantId;
        this.requestId = requestId;
        this.workflowId = workflowId;
        this.currentStepOrder = currentStepOrder;
        this.dueAt = dueAt;
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

    public boolean isOverdue(Instant now) {
        return now.isAfter(dueAt) || now.equals(dueAt);
    }

    public void escalateTo(int nextStepOrder, Instant nextDueAt) {
        this.currentStepOrder = nextStepOrder;
        this.dueAt = nextDueAt;
        this.escalated = true;
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

    public UUID getWorkflowId() {
        return workflowId;
    }

    public int getCurrentStepOrder() {
        return currentStepOrder;
    }

    public Instant getDueAt() {
        return dueAt;
    }

    public boolean isEscalated() {
        return escalated;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
