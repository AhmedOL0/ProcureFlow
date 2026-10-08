package com.procureflow.budget.infrastructure;

import com.procureflow.budget.domain.BudgetReservation;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Persistence adapter for budget reservations. Used only by the budget module. */
public interface BudgetReservationRepository extends JpaRepository<BudgetReservation, UUID> {

    Optional<BudgetReservation> findByTenantIdAndRequestId(UUID tenantId, UUID requestId);

    List<BudgetReservation> findAllByBudgetIdOrderByCreatedAtDesc(UUID budgetId);

    boolean existsByBudgetId(UUID budgetId);

    @Query("select coalesce(sum(r.amountMinor), 0) from BudgetReservation r where r.budgetId = :budgetId")
    long sumReservedByBudgetId(@Param("budgetId") UUID budgetId);
}
