package com.procureflow.identity.infrastructure;

import com.procureflow.identity.application.MailPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Local-development mail sink (the default: {@code app.mail.provider=log}).
 * Records that an email <em>would</em> have been sent — recipient and
 * subject only. Reset tokens and URLs are never logged here; the flow is
 * exercised in tests with a mocked {@link MailPort} instead.
 */
@Service
@ConditionalOnProperty(name = "app.mail.provider", havingValue = "log", matchIfMissing = true)
public class LogMailPort implements MailPort {

    private static final Logger log = LoggerFactory.getLogger(LogMailPort.class);

    @Override
    public void send(OutgoingMail mail) {
        log.info("Outbound mail suppressed (no SMTP configured): to={} subject={}", mail.to(), mail.subject());
    }
}
