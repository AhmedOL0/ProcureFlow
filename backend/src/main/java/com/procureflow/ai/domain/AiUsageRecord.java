package com.procureflow.ai.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * One metered provider call. Written with the copilot answer in the same
 * transaction; read by the usage endpoint. No update or delete path.
 */
@Entity
@Table(name = "ai_usage")
public class AiUsageRecord {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false, length = 40)
    private String feature;

    @Column(nullable = false, length = 100)
    private String model;

    @Column(name = "prompt_version", nullable = false, length = 20)
    private String promptVersion;

    @Column(name = "prompt_tokens", nullable = false)
    private int promptTokens;

    @Column(name = "completion_tokens", nullable = false)
    private int completionTokens;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AiUsageRecord() {
        // JPA only
    }

    public AiUsageRecord(
            UUID tenantId, String feature, String model, String promptVersion, int promptTokens,
            int completionTokens) {
        this.tenantId = tenantId;
        this.feature = feature;
        this.model = model;
        this.promptVersion = promptVersion;
        this.promptTokens = promptTokens;
        this.completionTokens = completionTokens;
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

    public UUID getTenantId() {
        return tenantId;
    }

    public String getFeature() {
        return feature;
    }

    public String getModel() {
        return model;
    }

    public String getPromptVersion() {
        return promptVersion;
    }

    public int getPromptTokens() {
        return promptTokens;
    }

    public int getCompletionTokens() {
        return completionTokens;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
