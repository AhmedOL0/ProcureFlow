package com.procureflow.audit.api;

import com.procureflow.audit.domain.AuditEvent;
import java.time.Instant;
import java.util.UUID;

public record AuditEventResponse(
        UUID id,
        UUID actorId,
        String action,
        String entityType,
        UUID entityId,
        String beforeJson,
        String afterJson,
        Instant createdAt) {

    public static AuditEventResponse from(AuditEvent event) {
        return new AuditEventResponse(
                event.getId(),
                event.getActorId(),
                event.getAction(),
                event.getEntityType(),
                event.getEntityId(),
                event.getBeforeJson(),
                event.getAfterJson(),
                event.getCreatedAt());
    }
}
