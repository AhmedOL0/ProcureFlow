package com.procureflow.procurement.infrastructure;

import com.procureflow.procurement.domain.PurchaseRequest;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Persistence adapter for purchase requests. Used only by the procurement module. */
public interface PurchaseRequestRepository extends JpaRepository<PurchaseRequest, UUID> {

    @EntityGraph(attributePaths = {"tenant", "items"})
    @Query("select r from PurchaseRequest r where r.tenant.slug = :slug order by r.createdAt desc")
    List<PurchaseRequest> findAllByTenantSlug(@Param("slug") String slug);



    @EntityGraph(attributePaths = {"tenant", "items"})
    @Query("select r from PurchaseRequest r where r.tenant.slug = :slug"
            + " and lower(r.title) like lower(concat('%', :query, '%')) order by r.createdAt desc")
    Page<PurchaseRequest> findPageByTenantSlug(
            @Param("slug") String slug, @Param("query") String query, Pageable pageable);

    @EntityGraph(attributePaths = {"tenant", "items"})
    @Query("select r from PurchaseRequest r where r.tenant.slug = :slug and r.status = :status"
            + " and lower(r.title) like lower(concat('%', :query, '%')) order by r.createdAt desc")
    Page<PurchaseRequest> findPageByTenantSlugAndStatus(
            @Param("slug") String slug,
            @Param("status") PurchaseRequest.Status status,
            @Param("query") String query,
            Pageable pageable);

    @EntityGraph(attributePaths = {"tenant", "items"})
    @Query("select r from PurchaseRequest r where r.tenant.slug = :slug and r.requester.id = :requesterId"
            + " and lower(r.title) like lower(concat('%', :query, '%')) order by r.createdAt desc")
    Page<PurchaseRequest> findPageByRequester(
            @Param("slug") String slug,
            @Param("requesterId") UUID requesterId,
            @Param("query") String query,
            Pageable pageable);

    @EntityGraph(attributePaths = {"tenant", "items"})
    @Query("select r from PurchaseRequest r where r.tenant.slug = :slug and r.requester.id = :requesterId"
            + " and r.status = :status and lower(r.title) like lower(concat('%', :query, '%'))"
            + " order by r.createdAt desc")
    Page<PurchaseRequest> findPageByRequesterAndStatus(
            @Param("slug") String slug,
            @Param("requesterId") UUID requesterId,
            @Param("status") PurchaseRequest.Status status,
            @Param("query") String query,
            Pageable pageable);

    @EntityGraph(attributePaths = {"tenant", "items"})
    @Query("select r from PurchaseRequest r where r.id = :id and r.tenant.slug = :slug")
    Optional<PurchaseRequest> findByIdAndTenantSlug(@Param("id") UUID id, @Param("slug") String slug);

    @EntityGraph(attributePaths = {"tenant", "items"})
    @Query("select r from PurchaseRequest r where r.tenant.slug = :slug and r.idempotencyKey = :key")
    Optional<PurchaseRequest> findByTenantSlugAndIdempotencyKey(
            @Param("slug") String slug, @Param("key") String key);

    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from PurchaseRequest r where r.id = :id and r.tenant.slug = :slug")
    Optional<PurchaseRequest> lockByIdAndTenantSlug(@Param("id") UUID id, @Param("slug") String slug);
}
