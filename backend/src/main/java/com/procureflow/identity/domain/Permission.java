package com.procureflow.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * A single fine-grained capability (for example {@code procurement:approve}).
 * Permissions are global codes shared by all tenants; roles bind them to
 * users inside one tenant.
 */
@Entity
@Table(name = "permissions")
public class Permission {

    @Id
    private UUID id;

    @Column(nullable = false, length = 150, unique = true)
    private String code;

    @Column(length = 500)
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Permission() {
        // JPA only
    }

    public Permission(String code, String description) {
        this.code = code;
        this.description = description;
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

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
