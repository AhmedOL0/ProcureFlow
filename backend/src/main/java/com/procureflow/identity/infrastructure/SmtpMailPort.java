package com.procureflow.identity.infrastructure;

import com.procureflow.identity.application.MailPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Brevo SMTP adapter (smtp-relay.brevo.com:587, STARTTLS — see
 * docs/security/secrets.md). Active only when
 * {@code app.mail.provider=smtp}; credentials stay in environment, never in
 * code or logs. SMTP failures propagate as {@link MailException} so callers
 * can log safely and keep client responses generic.
 */
@Service
@ConditionalOnProperty(name = "app.mail.provider", havingValue = "smtp")
public class SmtpMailPort implements MailPort {

    private static final Logger log = LoggerFactory.getLogger(SmtpMailPort.class);

    private final JavaMailSender sender;
    private final String from;

    public SmtpMailPort(JavaMailSender sender, @Value("${app.mail.from:no-reply@example.com}") String from) {
        this.sender = sender;
        this.from = from;
    }

    @Override
    public void send(OutgoingMail mail) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(mail.to());
        message.setSubject(mail.subject());
        message.setText(mail.textBody());
        try {
            sender.send(message);
        } catch (MailException e) {
            // Recipient only: bodies carry reset URLs and must never land in logs.
            log.warn("Outbound mail submission failed for recipient domain {}", domainOf(mail.to()));
            throw e;
        }
    }

    private static String domainOf(String address) {
        int at = address.lastIndexOf('@');
        return at >= 0 ? address.substring(at + 1) : "unknown";
    }
}
