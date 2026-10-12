package com.procureflow.procurement.infrastructure;

import com.procureflow.procurement.domain.PurchaseRequestItem;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Persistence adapter for request items. Used only by the procurement module. */
public interface PurchaseRequestItemRepository extends JpaRepository<PurchaseRequestItem, UUID> {

    List<PurchaseRequestItem> findAllByRequest_IdOrderByCreatedAt(UUID requestId);

    @Query("select i from PurchaseRequestItem i where i.id = :id and i.request.tenant.slug = :slug")
    Optional<PurchaseRequestItem> findByIdAndTenantSlug(@Param("id") UUID id, @Param("slug") String slug);

    long countByRequest_Id(UUID requestId);

    // DB-side sums for the all-time analytics path: no item entities load.
    // Cancelled requests never demanded anything, so they stay out here,
    // mirroring the in-memory filter on the month path.
    @Query("select coalesce(sum(i.quantity * i.unitPriceMinor), 0) from PurchaseRequestItem i"
            + " where i.request.tenant.slug = :slug"
            + " and i.request.status <> com.procureflow.procurement.domain.PurchaseRequest.Status.CANCELLED")
    long sumActiveByTenantSlug(@Param("slug") String slug);

    @Query("select i.category, coalesce(sum(i.quantity * i.unitPriceMinor), 0) from PurchaseRequestItem i"
            + " where i.request.tenant.slug = :slug"
            + " and i.request.status <> com.procureflow.procurement.domain.PurchaseRequest.Status.CANCELLED"
            + " group by i.category")
    List<Object[]> sumActiveByCategoryAndTenantSlug(@Param("slug") String slug);
}
