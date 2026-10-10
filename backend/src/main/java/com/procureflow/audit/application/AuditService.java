package com.procureflow.audit.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.procureflow.audit.domain.AuditEvent;
import com.procureflow.audit.infrastructure.AuditEventRepository;
import com.procureflow.organization.application.TenantProvisioning;
import com.procureflow.shared.web.ApiException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Append-only audit trail. Events persist AFTER_COMMIT in a fresh
 * transaction wrapped in a catch-and-log, so an audit failure can never
 * turn settled business work into a 500. Reads are admin-only at the API
 * boundary; rows are tenant-scoped (cross-tenant reads answer 404/empty).
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditEventRepository events;
    private final TenantProvisioning tenants;
    private final ObjectMapper json;

    public AuditService(AuditEventRepository events, TenantProvisioning tenants, ObjectMapper json) {
        this.events = events;
        this.tenants = tenants;
        this.json = json;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onAudit(AuditTrailLogged event) {
        try {
            events.save(new AuditEvent(
                    tenants.requireTenantId(event.tenantSlug()),
                    event.actorId(),
                    event.action(),
                    event.entityType(),
                    event.entityId(),
                    toJson(event.before()),
                    toJson(event.after())));
        } catch (RuntimeException e) {
            log.error("Audit write failed for action {} (business work already committed)", event.action(), e);
        }
    }

    @Transactional(readOnly = true)
    public List<AuditEvent> list(String tenantSlug, String entityType, UUID entityId) {
        UUID tenantId = tenants.requireTenantId(tenantSlug);
        if (entityType == null && entityId == null) {
            return events.findAllByTenantIdOrderByCreatedAtDesc(tenantId);
        }
        if (entityType != null && entityId == null) {
            return events.findAllByTenantIdAndEntityTypeOrderByCreatedAtDesc(tenantId, entityType);
        }
        if (entityType == null) {
            return events.findAllByTenantIdAndEntityIdOrderByCreatedAtDesc(tenantId, entityId);
        }
        return events.findAllByTenantIdAndEntityTypeAndEntityIdOrderByCreatedAtDesc(tenantId, entityType, entityId);
    }

    private String toJson(Map<String, Object> payload) {
        if (payload == null) {
            return null;
        }
        try {
            return json.writeValueAsString(redacted(payload));
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw ApiException.badRequest("AUDIT_PAYLOAD", "Audit payload is not serializable");
        }
    }

    /**
     * Central sensitive-field guard. Producers are told to send plain maps,
     * but a single stray {@code passwordHash} or bearer token must never
     * reach a stored, admin-readable row — so keys naming secrets are masked
     * here, one level deep, regardless of producer discipline.
     */
    static Map<String, Object> redacted(Map<String, Object> payload) {
        LinkedHashMap<String, Object> safe = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : payload.entrySet()) {
            String key = entry.getKey() == null ? "" : entry.getKey().toLowerCase(Locale.ROOT);
            if (key.contains("password") || key.contains("token") || key.contains("secret")
                    || key.contains("authorization") || key.contains("apikey") || key.contains("api_key")) {
                safe.put(entry.getKey(), "[REDACTED]");
            } else {
                safe.put(entry.getKey(), entry.getValue());
            }
        }
        return safe;
    }
}
