package com.procureflow.organization.domain;

import com.procureflow.identity.domain.User;
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
 * Links a user to a department. A user may belong to several departments;
 * approval delegation and department-scoped visibility build on this table.
 */
@Entity
@Table(
        name = "memberships",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "department_id"}))
public class Membership {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "department_id", nullable = false)
    private Department department;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Membership() {
        // JPA only
    }

    public Membership(User user, Department department) {
        this.user = user;
        this.department = department;
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

    public User getUser() {
        return user;
    }

    public Department getDepartment() {
        return department;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
