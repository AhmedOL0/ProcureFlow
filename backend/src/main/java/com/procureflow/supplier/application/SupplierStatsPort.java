package com.procureflow.supplier.application;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Cross-module read port: monthly supplier scorecards for analytics.
 * Plain values only; entities never cross modules.
 */
public interface SupplierStatsPort {

    List<ScoreStat> scores(String tenantSlug);

    record ScoreStat(UUID supplierId, String supplierName, String period, BigDecimal onTimeRate,
            BigDecimal qualityScore) {
    }
}
