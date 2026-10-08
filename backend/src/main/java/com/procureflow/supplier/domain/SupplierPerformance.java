package com.procureflow.supplier.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One monthly scorecard row per supplier: on-time delivery and quality as
 * 0-100 scores plus a free-text note. Feeds the supplier KPIs of the
 * analytics epic; one row per supplier and month, enforced unique.
 */
@Entity
@Table(
        name = "supplier_performances",
        uniqueConstraints = @UniqueConstraint(columnNames = {"supplier_id", "period"}))
public class SupplierPerformance {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "supplier_id", nullable = false)
    private Supplier supplier;

    @Column(nullable = false, length = 7)
    private String period;

    @Column(name = "on_time_rate", precision = 5, scale = 2)
    private BigDecimal onTimeRate;

    @Column(name = "quality_score", precision = 5, scale = 2)
    private BigDecimal qualityScore;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected SupplierPerformance() {
        // JPA only
    }

    public SupplierPerformance(
            Supplier supplier, String period, BigDecimal onTimeRate, BigDecimal qualityScore, String notes) {
        this.supplier = supplier;
        this.period = period;
        this.onTimeRate = onTimeRate;
        this.qualityScore = qualityScore;
        this.notes = notes;
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

    public Supplier getSupplier() {
        return supplier;
    }

    public String getPeriod() {
        return period;
    }

    public BigDecimal getOnTimeRate() {
        return onTimeRate;
    }

    public BigDecimal getQualityScore() {
        return qualityScore;
    }

    public String getNotes() {
        return notes;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
