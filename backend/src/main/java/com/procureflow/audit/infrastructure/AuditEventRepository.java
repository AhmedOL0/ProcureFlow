package com.procureflow.audit.infrastructure;

import com.procureflow.audit.domain.AuditEvent;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence adapter for the audit trail. Writes only; nothing updates or deletes. */
public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {

    Page<AuditEvent> findAllByTenantIdOrderByCreatedAtDesc(UUID tenantId, Pageable pageable);

    Page<AuditEvent> findAllByTenantIdAndEntityTypeOrderByCreatedAtDesc(
            UUID tenantId, String entityType, Pageable pageable);

    Page<AuditEvent> findAllByTenantIdAndEntityIdOrderByCreatedAtDesc(
            UUID tenantId, UUID entityId, Pageable pageable);

    Page<AuditEvent> findAllByTenantIdAndEntityTypeAndEntityIdOrderByCreatedAtDesc(
            UUID tenantId, String entityType, UUID entityId, Pageable pageable);
}
