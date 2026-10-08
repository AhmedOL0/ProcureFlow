package com.procureflow.procurement.domain;

import com.procureflow.identity.domain.User;
import com.procureflow.organization.domain.Tenant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A purchase request: what someone wants to buy, why, and where it stands.
 * Lifecycle in Phase 3: DRAFT → SUBMITTED → CANCELLED (by owner or admin).
 * APPROVED/REJECTED/ORDERED arrive with the approval workflow (Phase 4) and
 * purchase orders (Phase 5); the states exist here so later transitions
 * need no schema change.
 */
@Entity
@Table(
        name = "purchase_requests",
        uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_id", "idempotency_key"}))
public class PurchaseRequest {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requester_id", nullable = false)
    private User requester;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Priority priority = Priority.MEDIUM;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.DRAFT;

    @Column(name = "idempotency_key", nullable = false, length = 100)
    private String idempotencyKey;

    // Inverse side for reads; writes always go through the item repository
    // explicitly (no cascade), so item lifecycle stays obvious.
    @OneToMany(mappedBy = "request", fetch = FetchType.LAZY)
    private List<PurchaseRequestItem> items = new ArrayList<>();

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PurchaseRequest() {
        // JPA only
    }

    public PurchaseRequest(Tenant tenant, User requester, String title, String idempotencyKey) {
        this.tenant = tenant;
        this.requester = requester;
        this.title = title;
        this.idempotencyKey = idempotencyKey;
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

    public UUID getId() {
        return id;
    }

    public Tenant getTenant() {
        return tenant;
    }

    public User getRequester() {
        return requester;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Priority getPriority() {
        return priority;
    }

    public void setPriority(Priority priority) {
        this.priority = priority;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public List<PurchaseRequestItem> getItems() {
        return items;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(Instant submittedAt) {
        this.submittedAt = submittedAt;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public void setDecidedAt(Instant decidedAt) {
        this.decidedAt = decidedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public enum Priority {
        LOW,
        MEDIUM,
        HIGH,
        URGENT
    }

    public enum Status {
        DRAFT,
        SUBMITTED,
        APPROVED,
        REJECTED,
        ORDERED,
        CANCELLED
    }
}
