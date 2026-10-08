package com.procureflow.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import com.procureflow.organization.domain.Tenant;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Named bundle of permissions, scoped to one tenant. Roles are assigned to
 * users; permissions are never assigned to users directly.
 */
@Entity
@Table(
        name = "roles",
        uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_id", "name"}))
public class Role {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 500)
    private String description;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "role_permissions",
            joinColumns = @JoinColumn(name = "role_id"),
            inverseJoinColumns = @JoinColumn(name = "permission_id"))
    private Set<Permission> permissions = new HashSet<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Role() {
        // JPA only
    }

    public Role(Tenant tenant, String name) {
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

    public String getDescription() {
        return description;
    }

    public Set<Permission> getPermissions() {
        return permissions;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
