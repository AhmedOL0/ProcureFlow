package com.procureflow.audit.infrastructure;

import com.procureflow.audit.domain.AuditEvent;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence adapter for the audit trail. Writes only; nothing updates or deletes. */
public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {

    List<AuditEvent> findAllByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    List<AuditEvent> findAllByTenantIdAndEntityTypeOrderByCreatedAtDesc(UUID tenantId, String entityType);

    List<AuditEvent> findAllByTenantIdAndEntityIdOrderByCreatedAtDesc(UUID tenantId, UUID entityId);

    List<AuditEvent> findAllByTenantIdAndEntityTypeAndEntityIdOrderByCreatedAtDesc(
            UUID tenantId, String entityType, UUID entityId);
}
