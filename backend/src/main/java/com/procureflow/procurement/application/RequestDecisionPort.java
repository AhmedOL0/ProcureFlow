package com.procureflow.procurement.application;

import java.util.UUID;

/**
 * Cross-module port: approval decisions lock a submitted request row and flip
 * its status in the same transaction. Primitives and DTOs only; the purchase
 * request entity never crosses module boundaries.
 */
public interface RequestDecisionPort {

    /** Locks the request row (FOR UPDATE); 404 when absent or foreign to the tenant. */
    LockedRequest lock(String tenantSlug, UUID requestId);

    /** Applies APPROVED/REJECTED plus decidedAt; call while holding the lock in the same transaction. */
    void applyDecision(String tenantSlug, UUID requestId, boolean approved);

    /** The locked request as plain values: status name, owning requester, and item total in minor units. */
    record LockedRequest(UUID requestId, String status, UUID requesterId, long totalMinor) {
    }
}
