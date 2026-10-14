package com.procureflow.supplier.application;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Cross-module read port: monthly supplier scorecards for analytics.
 * Plain values only; entities never cross modules.
 */
public interface SupplierStatsPort {

    /**
     * One grouped row per supplier: exact sums plus non-null counts so the
     * caller divides with scale-2 HALF_UP semantics, matching the row scan.
     */
    List<ScoreSummary> scoreSummaries(String tenantSlug);

    record ScoreSummary(UUID supplierId, String supplierName, long periods, BigDecimal onTimeSum, long onTimeCount,
            BigDecimal qualitySum, long qualityCount) {
    }
}
