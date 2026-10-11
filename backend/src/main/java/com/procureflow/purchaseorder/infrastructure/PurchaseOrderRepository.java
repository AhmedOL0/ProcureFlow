package com.procureflow.purchaseorder.infrastructure;

import com.procureflow.purchaseorder.domain.PurchaseOrder;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Persistence adapter for purchase orders. Used only by the purchaseorder module. */
public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, UUID> {

    Optional<PurchaseOrder> findByIdAndTenantId(UUID id, UUID tenantId);

    List<PurchaseOrder> findAllByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    // Half-open month range (inclusive start, exclusive end): adjacent
    // months never share a row, and nulls are impossible by construction.
    @Query("select o from PurchaseOrder o where o.tenantId = :tenantId"
            + " and o.createdAt >= :from and o.createdAt < :to order by o.createdAt desc")
    List<PurchaseOrder> findAllByTenantIdAndCreatedAtBetween(
            @Param("tenantId") UUID tenantId, @Param("from") Instant from, @Param("to") Instant to);

    Page<PurchaseOrder> findAllByTenantIdOrderByCreatedAtDesc(UUID tenantId, Pageable pageable);

    Page<PurchaseOrder> findAllByTenantIdAndStatusOrderByCreatedAtDesc(
            UUID tenantId, PurchaseOrder.Status status, Pageable pageable);

    boolean existsByRequestId(UUID requestId);

    boolean existsByTenantIdAndSupplierId(UUID tenantId, UUID supplierId);

    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from PurchaseOrder o where o.id = :id and o.tenantId = :tenantId")
    Optional<PurchaseOrder> lockByIdAndTenantId(@Param("id") UUID id, @Param("tenantId") UUID tenantId);
}
