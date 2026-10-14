package com.procureflow.supplier.application;

import com.procureflow.supplier.infrastructure.SupplierPerformanceRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.UUID;
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
    public List<ScoreSummary> scoreSummaries(String tenantSlug) {
        return performances.scoreSummariesByTenantSlug(tenantSlug).stream()
                .map(row -> new ScoreSummary(
                        (UUID) row[0],
                        (String) row[1],
                        ((Number) row[2]).longValue(),
                        (BigDecimal) row[3],
                        ((Number) row[4]).longValue(),
                        (BigDecimal) row[5],
                        ((Number) row[6]).longValue()))
                .toList();
    }
}
