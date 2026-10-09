package com.procureflow.budget.application;

import java.util.UUID;

/**
 * Cross-module port: approvals reserve a request total against the period
 * budget in the same transaction as the status change, and procurement
 * releases it when an approved request is cancelled. Primitives only.
 */
public interface BudgetReservationPort {

    /** Holds {@code amountMinor} for one request; 409 when the period budget cannot cover it. */
    void reserve(String tenantSlug, UUID requestId, long amountMinor, String currency);

    /** Releases the request's hold; a no-op when nothing is held. */
    void release(String tenantSlug, UUID requestId);
}
