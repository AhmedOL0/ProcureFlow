package com.procureflow.notification.application;

import java.util.UUID;

/**
 * Outbound mail port. The only implementation is a logging skeleton until a
 * real SMTP transport lands; nothing in the product depends on delivery.
 */
public interface NotificationTransport {

    void send(UUID tenantId, UUID recipientId, String subject, String body);
}
