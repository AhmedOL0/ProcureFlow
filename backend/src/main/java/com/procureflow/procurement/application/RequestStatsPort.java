package com.procureflow.procurement.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Cross-module read port: request volumes, statuses and lead-time stamps
 * for analytics. Plain values only; entities never cross modules.
 */
public interface RequestStatsPort {

    List<RequestStat> requestStats(String tenantSlug);

    /** Month-bounded variant: half-open [from, to), never null at the call site. */
    List<RequestStat> requestStats(String tenantSlug, Instant from, Instant to);

    /** DB-side sums for the all-time path: no request or item entities load. */
    RequestTotals requestTotals(String tenantSlug);

    /**
     * Approval KPIs in one grouped row: no request entities load however
     * large tenant history grows. Lead sums arrive in millis (null when no
     * decided row carries both stamps); the caller rounds exactly once.
     */
    ApprovalSummary approvalSummary(String tenantSlug);

    record RequestStat(
            UUID requestId,
            String status,
            String currency,
            Instant createdAt,
            Instant submittedAt,
            Instant decidedAt,
            List<ItemStat> items) {
    }

    record ItemStat(String category, int quantity, long unitPriceMinor, String currency) {
    }

    record CategoryTotal(String category, String currency, long amountMinor) {
    }

    record RequestTotals(long requestedMinor, List<CategoryTotal> byCategory) {
    }

    record ApprovalSummary(long pending, long decided, Double leadMillisSum, Double leadMillisMax) {
    }
}
