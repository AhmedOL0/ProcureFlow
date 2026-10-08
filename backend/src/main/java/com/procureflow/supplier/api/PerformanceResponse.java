package com.procureflow.supplier.api;

import com.procureflow.supplier.domain.SupplierPerformance;
import java.math.BigDecimal;
import java.util.UUID;

public record PerformanceResponse(
        UUID id,
        UUID supplierId,
        String period,
        BigDecimal onTimeRate,
        BigDecimal qualityScore,
        String notes) {

    public static PerformanceResponse from(SupplierPerformance performance) {
        return new PerformanceResponse(
                performance.getId(),
                performance.getSupplier().getId(),
                performance.getPeriod(),
                performance.getOnTimeRate(),
                performance.getQualityScore(),
                performance.getNotes());
    }
}
