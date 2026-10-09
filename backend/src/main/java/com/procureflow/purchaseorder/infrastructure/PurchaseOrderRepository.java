package com.procureflow.purchaseorder.infrastructure;

import com.procureflow.purchaseorder.domain.PurchaseOrder;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Persistence adapter for purchase orders. Used only by the purchaseorder module. */
public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, UUID> {

    Optional<PurchaseOrder> findByIdAndTenantId(UUID id, UUID tenantId);

    List<PurchaseOrder> findAllByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    List<PurchaseOrder> findAllByTenantIdAndStatusOrderByCreatedAtDesc(UUID tenantId, PurchaseOrder.Status status);

    boolean existsByRequestId(UUID requestId);

    boolean existsByTenantIdAndSupplierId(UUID tenantId, UUID supplierId);

    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from PurchaseOrder o where o.id = :id and o.tenantId = :tenantId")
    Optional<PurchaseOrder> lockByIdAndTenantId(@Param("id") UUID id, @Param("tenantId") UUID tenantId);
}
