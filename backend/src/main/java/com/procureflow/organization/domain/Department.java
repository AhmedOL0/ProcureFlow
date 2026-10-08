package com.procureflow.organization.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;

/**
 * Organizational unit inside a tenant. Departments form an optional tree
 * (a department may have a parent) and gate approval routing in Phase 4.
 */
@Entity
@Table(
        name = "departments",
        uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_id", "name"}))
public class Department {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Column(nullable = false, length = 200)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Department parent;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Department() {
        // JPA only
    }

    public Department(Tenant tenant, String name) {
        this.tenant = tenant;
        this.name = name;
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

    public Tenant getTenant() {
        return tenant;
    }

    public String getName() {
        return name;
    }

    public Department getParent() {
        return parent;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
