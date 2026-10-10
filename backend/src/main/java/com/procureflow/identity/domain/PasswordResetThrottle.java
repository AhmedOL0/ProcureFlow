package com.procureflow.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Throttle ledger for the password-reset flow. Buckets are opaque strings
 * ({@code email:<sha256>} or {@code token:<sha256>}) so no credential or
 * token material accumulates here — only counts over time windows.
 */
@Entity
@Table(name = "password_reset_throttle")
public class PasswordResetThrottle {

    @Id
    private UUID id;

    @Column(nullable = false, length = 128)
    private String bucket;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private Instant requestedAt;

    protected PasswordResetThrottle() {
        // JPA only
    }

    public PasswordResetThrottle(String bucket, Instant requestedAt) {
        this.bucket = bucket;
        this.requestedAt = requestedAt;
    }

    public UUID getId() {
        return id;
    }

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (requestedAt == null) {
            requestedAt = Instant.now();
        }
    }
}
