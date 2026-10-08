package com.procureflow.approval.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;

/**
 * One ordered level of a workflow. The approver is referenced by id only;
 * membership is validated at write time via the identity port.
 */
@Entity
@Table(name = "approval_steps", uniqueConstraints = @UniqueConstraint(columnNames = {"workflow_id", "step_order"}))
public class ApprovalStep {

    @Id
    private UUID id;

    @Column(name = "workflow_id", nullable = false)
    private UUID workflowId;

    @Column(name = "step_order", nullable = false)
    private int stepOrder;

    @Column(name = "approver_id", nullable = false)
    private UUID approverId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ApprovalStep() {
        // JPA only
    }

    public ApprovalStep(UUID workflowId, int stepOrder, UUID approverId) {
        this.workflowId = workflowId;
        this.stepOrder = stepOrder;
        this.approverId = approverId;
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

    public UUID getWorkflowId() {
        return workflowId;
    }

    public int getStepOrder() {
        return stepOrder;
    }

    public UUID getApproverId() {
        return approverId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
