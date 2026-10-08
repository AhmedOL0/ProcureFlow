package com.procureflow.procurement.application;

import com.procureflow.shared.kernel.DomainEvent;
import java.time.Instant;
import java.util.UUID;

/**
 * A draft became SUBMITTED. The notification module consumes this to tell
 * the workspace; publishers never change when the transport does (in-process
 * Spring events today, RabbitMQ later).
 */
public record PurchaseRequestSubmitted(
        UUID eventId,
        Instant occurredAt,
        String tenantSlug,
        UUID requestId,
        UUID requesterId,
        String title,
        long totalMinor) implements DomainEvent {

    public static PurchaseRequestSubmitted now(
            String tenantSlug, UUID requestId, UUID requesterId, String title, long totalMinor) {
        return new PurchaseRequestSubmitted(
                UUID.randomUUID(), Instant.now(), tenantSlug, requestId, requesterId, title, totalMinor);
    }
}
