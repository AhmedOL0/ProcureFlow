package com.procureflow.audit.infrastructure;

import com.procureflow.audit.domain.AuditEvent;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Persistence adapter for the audit trail. Writes only; nothing updates or deletes. */
public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {

    Page<AuditEvent> findAllByTenantIdOrderByCreatedAtDesc(UUID tenantId, Pageable pageable);

    Page<AuditEvent> findAllByTenantIdAndEntityTypeOrderByCreatedAtDesc(
            UUID tenantId, String entityType, Pageable pageable);

    Page<AuditEvent> findAllByTenantIdAndEntityIdOrderByCreatedAtDesc(
            UUID tenantId, UUID entityId, Pageable pageable);

    Page<AuditEvent> findAllByTenantIdAndEntityTypeAndEntityIdOrderByCreatedAtDesc(
            UUID tenantId, String entityType, UUID entityId, Pageable pageable);

    // NOTE: no nullable parameters anywhere in these queries (see the
    // supplier repository note): the service normalizes a blank q to ""
    // and only calls these when a real search term exists.
    @Query("select e from AuditEvent e where e.tenantId = :tenantId"
            + " and (lower(e.action) like lower(concat('%', :query, '%'))"
            + " or lower(e.entityType) like lower(concat('%', :query, '%')))"
            + " order by e.createdAt desc")
    Page<AuditEvent> searchByTenantIdOrderByCreatedAtDesc(
            @Param("tenantId") UUID tenantId, @Param("query") String query, Pageable pageable);

    @Query("select e from AuditEvent e where e.tenantId = :tenantId and e.entityType = :entityType"
            + " and (lower(e.action) like lower(concat('%', :query, '%'))"
            + " or lower(e.entityType) like lower(concat('%', :query, '%')))"
            + " order by e.createdAt desc")
    Page<AuditEvent> searchByTenantIdAndEntityTypeOrderByCreatedAtDesc(
            @Param("tenantId") UUID tenantId,
            @Param("entityType") String entityType,
            @Param("query") String query,
            Pageable pageable);

    @Query("select e from AuditEvent e where e.tenantId = :tenantId and e.entityId = :entityId"
            + " and (lower(e.action) like lower(concat('%', :query, '%'))"
            + " or lower(e.entityType) like lower(concat('%', :query, '%')))"
            + " order by e.createdAt desc")
    Page<AuditEvent> searchByTenantIdAndEntityIdOrderByCreatedAtDesc(
            @Param("tenantId") UUID tenantId,
            @Param("entityId") UUID entityId,
            @Param("query") String query,
            Pageable pageable);

    @Query("select e from AuditEvent e where e.tenantId = :tenantId"
            + " and e.entityType = :entityType and e.entityId = :entityId"
            + " and (lower(e.action) like lower(concat('%', :query, '%'))"
            + " or lower(e.entityType) like lower(concat('%', :query, '%')))"
            + " order by e.createdAt desc")
    Page<AuditEvent> searchByTenantIdAndEntityTypeAndEntityIdOrderByCreatedAtDesc(
            @Param("tenantId") UUID tenantId,
            @Param("entityType") String entityType,
            @Param("entityId") UUID entityId,
            @Param("query") String query,
            Pageable pageable);
}
