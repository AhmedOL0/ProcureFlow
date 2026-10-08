package com.procureflow.supplier.infrastructure;

import com.procureflow.supplier.domain.SupplierPerformance;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Persistence adapter for performance scorecards. Used only by the supplier module. */
public interface SupplierPerformanceRepository extends JpaRepository<SupplierPerformance, UUID> {

    @Query("select p from SupplierPerformance p where p.supplier.id = :supplierId"
            + " and p.supplier.tenant.slug = :slug order by p.period desc")
    List<SupplierPerformance> findAllBySupplier(@Param("supplierId") UUID supplierId, @Param("slug") String slug);

    boolean existsBySupplier_IdAndPeriod(UUID supplierId, String period);
}
