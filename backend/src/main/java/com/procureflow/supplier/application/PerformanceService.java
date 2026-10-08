package com.procureflow.supplier.application;

import com.procureflow.shared.web.ApiException;
import com.procureflow.supplier.domain.Supplier;
import com.procureflow.supplier.domain.SupplierPerformance;
import com.procureflow.supplier.infrastructure.SupplierPerformanceRepository;
import com.procureflow.supplier.infrastructure.SupplierRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Monthly supplier scorecards. One row per supplier and month; duplicates
 * are rejected so history stays append-only and comparable.
 */
@Service
@Transactional
public class PerformanceService {

    private final SupplierPerformanceRepository performances;
    private final SupplierRepository suppliers;

    public PerformanceService(
            SupplierPerformanceRepository performances, SupplierRepository suppliers) {
        this.performances = performances;
        this.suppliers = suppliers;
    }

    @Transactional(readOnly = true)
    public List<SupplierPerformance> list(String tenantSlug, UUID supplierId) {
        scopedSupplier(tenantSlug, supplierId);
        return performances.findAllBySupplier(supplierId, tenantSlug);
    }

    public SupplierPerformance record(
            String tenantSlug,
            UUID supplierId,
            String period,
            BigDecimal onTimeRate,
            BigDecimal qualityScore,
            String notes) {
        Supplier supplier = scopedSupplier(tenantSlug, supplierId);
        if (performances.existsBySupplier_IdAndPeriod(supplierId, period)) {
            throw ApiException.conflict(
                    "PERFORMANCE_EXISTS", "A scorecard for this period already exists");
        }
        return performances.save(new SupplierPerformance(supplier, period, onTimeRate, qualityScore, notes));
    }

    private Supplier scopedSupplier(String tenantSlug, UUID supplierId) {
        return suppliers
                .findByIdAndTenantSlug(supplierId, tenantSlug)
                .orElseThrow(() -> ApiException.notFound("SUPPLIER_NOT_FOUND", "Supplier not found"));
    }
}
