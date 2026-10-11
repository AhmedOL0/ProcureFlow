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
}
