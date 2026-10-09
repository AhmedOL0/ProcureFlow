package com.procureflow.approval.application;

import com.procureflow.shared.kernel.DomainEvent;
import java.time.Instant;
import java.util.UUID;

/**
 * A submitted request received its immutable verdict. The notification
 * module consumes this to tell the requester; publishers never change when
 * the transport does (in-process Spring events today, RabbitMQ later).
 */
public record PurchaseRequestDecided(
        UUID eventId,
        Instant occurredAt,
        String tenantSlug,
        UUID requestId,
        UUID requesterId,
        UUID deciderId,
        boolean approved,
        String comment) implements DomainEvent {

    public static PurchaseRequestDecided now(
            String tenantSlug,
            UUID requestId,
            UUID requesterId,
            UUID deciderId,
            boolean approved,
            String comment) {
        return new PurchaseRequestDecided(
                UUID.randomUUID(),
                Instant.now(),
                tenantSlug,
                requestId,
                requesterId,
                deciderId,
                approved,
                comment);
    }
}
