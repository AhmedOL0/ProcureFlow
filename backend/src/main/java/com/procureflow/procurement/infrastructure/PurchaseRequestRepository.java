package com.procureflow.procurement.infrastructure;

import com.procureflow.procurement.domain.PurchaseRequest;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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

    // NOTE: explicit fetch join (not the graph above) so one month of rows
    // costs exactly one query: the graph would secondary-select items per
    // request, and the analytics range is the hot path this guards.
    @Query("select distinct r from PurchaseRequest r left join fetch r.items"
            + " where r.tenant.slug = :slug and r.createdAt >= :from and r.createdAt < :to"
            + " order by r.createdAt desc")
    List<PurchaseRequest> findAllByTenantSlugAndCreatedAtBetween(
            @Param("slug") String slug, @Param("from") Instant from, @Param("to") Instant to);



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

    @Query("select r from PurchaseRequest r where r.tenant.slug = :slug and r.id in :ids")
    List<PurchaseRequest> findByIdInAndTenantSlug(@Param("ids") Set<UUID> ids, @Param("slug") String slug);

    // Approval KPIs in one grouped row: pending counts every SUBMITTED row
    // (no stamp filter, matching the service contract); decided rows need
    // both stamps. Lead sums stay in millis so the service rounds exactly
    // once, the same rounding the row scan applied per row.
    // Native SQL: the EXTRACT(EPOCH FROM interval) lead arithmetic has no
    // JPQL spelling, and this product is Postgres-only by migration chain.
    // Declared as a list (one row: aggregates without GROUP BY always
    // return exactly one row, even over an empty table) following the
    // leadRows convention this replaces.
    @Query(value = "select "
            + "count(*) filter (where r.status = 'SUBMITTED'), "
            + "count(*) filter (where r.status in ('APPROVED', 'REJECTED', 'ORDERED')"
            + " and r.submitted_at is not null and r.decided_at is not null), "
            + "sum(extract(epoch from (r.decided_at - r.submitted_at)) * 1000)"
            + " filter (where r.status in ('APPROVED', 'REJECTED', 'ORDERED')"
            + " and r.submitted_at is not null and r.decided_at is not null), "
            + "max(extract(epoch from (r.decided_at - r.submitted_at)) * 1000)"
            + " filter (where r.status in ('APPROVED', 'REJECTED', 'ORDERED')"
            + " and r.submitted_at is not null and r.decided_at is not null) "
            + "from purchase_requests r join tenants t on t.id = r.tenant_id where t.slug = :slug",
            nativeQuery = true)
    List<Object[]> approvalSummaryByTenantSlug(@Param("slug") String slug);

    @EntityGraph(attributePaths = {"tenant", "items"})
    @Query("select r from PurchaseRequest r where r.tenant.slug = :slug and r.idempotencyKey = :key")
    Optional<PurchaseRequest> findByTenantSlugAndIdempotencyKey(
            @Param("slug") String slug, @Param("key") String key);

    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from PurchaseRequest r where r.id = :id and r.tenant.slug = :slug")
    Optional<PurchaseRequest> lockByIdAndTenantSlug(@Param("id") UUID id, @Param("slug") String slug);
}
