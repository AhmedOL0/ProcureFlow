package com.procureflow.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Throttle ledger for the authentication endpoints (register, login,
 * refresh). Buckets are opaque strings ({@code login:account:<sha256>} or
 * {@code <flow>:ip:<sha256>}) so no email or address material accumulates
 * here — only counts over time windows. Mirrors the password-reset ledger;
 * kept as its own table so auth and reset budgets never share a window.
 */
@Entity
@Table(name = "auth_throttle")
public class AuthThrottle {

    @Id
    private UUID id;

    @Column(nullable = false, length = 128)
    private String bucket;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private Instant requestedAt;

    protected AuthThrottle() {
        // JPA only
    }

    public AuthThrottle(String bucket, Instant requestedAt) {
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
