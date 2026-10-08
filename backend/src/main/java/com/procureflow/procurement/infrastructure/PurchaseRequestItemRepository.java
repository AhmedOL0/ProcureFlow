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
}
