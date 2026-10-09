package com.procureflow.notification.application;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Skeleton mail transport: logs instead of sending. A real SMTP
 * implementation replaces this class when the notifications epic grows one;
 * callers depend on the port, never on this class.
 */
@Service
public class LoggingMailTransport implements NotificationTransport {

    private static final Logger log = LoggerFactory.getLogger(LoggingMailTransport.class);

    @Override
    public void send(UUID tenantId, UUID recipientId, String subject, String body) {
        log.info("Mail skeleton to user {} in tenant {}: {} — {}", recipientId, tenantId, subject, body);
    }
}
