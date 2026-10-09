package com.procureflow.supplier.application;

import com.procureflow.supplier.infrastructure.SupplierPerformanceRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Default {@link SupplierStatsPort} over the supplier module's own repository. */
@Service
public class SupplierStatsAdapter implements SupplierStatsPort {

    private final SupplierPerformanceRepository performances;

    public SupplierStatsAdapter(SupplierPerformanceRepository performances) {
        this.performances = performances;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ScoreStat> scores(String tenantSlug) {
        return performances.findAllByTenantSlug(tenantSlug).stream()
                .map(p -> new ScoreStat(
                        p.getSupplier().getId(),
                        p.getSupplier().getName(),
                        p.getPeriod(),
                        p.getOnTimeRate(),
                        p.getQualityScore()))
                .toList();
    }
}
