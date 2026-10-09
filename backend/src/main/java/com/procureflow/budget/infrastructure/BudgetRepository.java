package com.procureflow.budget.infrastructure;

import com.procureflow.budget.domain.Budget;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Persistence adapter for budgets. Used only by the budget module. */
public interface BudgetRepository extends JpaRepository<Budget, UUID> {

    Optional<Budget> findByIdAndTenantId(UUID id, UUID tenantId);

    List<Budget> findAllByTenantIdOrderByPeriodDescNameAsc(UUID tenantId);

    List<Budget> findAllByTenantIdAndPeriodOrderByNameAsc(UUID tenantId, String period);

    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Budget b where b.tenantId = :tenantId and b.period = :period"
            + " and b.departmentId is null order by b.id")
    List<Budget> lockPeriodBudgets(@Param("tenantId") UUID tenantId, @Param("period") String period);
}
