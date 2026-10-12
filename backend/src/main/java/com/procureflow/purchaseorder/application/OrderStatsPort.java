package com.procureflow.purchaseorder.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Cross-module read port: order volumes and statuses for analytics.
 * Plain values only; entities never cross modules.
 */
public interface OrderStatsPort {

    List<OrderStat> orderStats(String tenantSlug);

    /** Month-bounded variant: half-open [from, to), never null at the call site. */
    List<OrderStat> orderStats(String tenantSlug, Instant from, Instant to);

    record OrderStat(UUID orderId, String status, String currency, Instant createdAt, List<LineStat> lines) {
    }

    record LineStat(int quantity, long unitPriceMinor, String currency, int receivedQty) {
    }

    /** DB-side totals for the all-time path: statuses and stamps with summed lines, no line entities. */
    record OrderTotal(UUID orderId, String status, Instant createdAt, long totalMinor) {
    }

    List<OrderTotal> orderTotals(String tenantSlug);
}
