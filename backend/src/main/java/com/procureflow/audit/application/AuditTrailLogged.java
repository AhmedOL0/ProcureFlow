package com.procureflow.audit.application;

import com.procureflow.shared.kernel.DomainEvent;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * A sensitive action that already happened. Producers publish this next to
 * their state change; the audit module persists it after commit. Payloads
 * are plain maps without secrets; the listener serializes them to JSON.
 */
public record AuditTrailLogged(
        UUID eventId,
        Instant occurredAt,
        String tenantSlug,
        UUID actorId,
        String action,
        String entityType,
        UUID entityId,
        Map<String, Object> before,
        Map<String, Object> after) implements DomainEvent {

    public static AuditTrailLogged now(
            String tenantSlug,
            UUID actorId,
            String action,
            String entityType,
            UUID entityId,
            Map<String, Object> before,
            Map<String, Object> after) {
        return new AuditTrailLogged(
                UUID.randomUUID(), Instant.now(), tenantSlug, actorId, action, entityType, entityId, before,
                after);
    }
}
