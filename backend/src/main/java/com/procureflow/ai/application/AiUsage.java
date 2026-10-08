package com.procureflow.ai.application;

import java.time.Instant;

/**
 * One metered AI call. Persisted per tenant and feature in Phase 6 so spend
 * can be reported, budgeted and rate-limited. Kept as a record now; the
 * entity mapping arrives with the persistence for it.
 */
public record AiUsage(
        String tenantId,
        String feature,
        String model,
        int promptTokens,
        int completionTokens,
        Instant requestedAt) {
}
