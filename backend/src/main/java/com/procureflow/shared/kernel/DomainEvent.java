package com.procureflow.shared.kernel;

import java.time.Instant;
import java.util.UUID;

/**
 * Marker for facts that already happened and that other modules may react to
 * (notifications, audit, analytics). Events are immutable records; publishing
 * stays in-process via Spring events, and a broker arrives only with a
 * second deployable consumer (ADR-010) without changing the publishers.
 */
public interface DomainEvent {

    UUID eventId();

    Instant occurredAt();
}
