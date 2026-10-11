package com.procureflow.identity.application;

import com.procureflow.identity.domain.EmailVerificationToken;
import com.procureflow.identity.domain.User;
import com.procureflow.identity.infrastructure.EmailVerificationTokenRepository;
import com.procureflow.identity.infrastructure.UserRepository;
import com.procureflow.shared.web.ApiException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Mailbox-ownership proof. A verification link goes out at registration (and
 * on resend); redeeming it flips the account verified. Only verified
 * accounts mint password-reset links — otherwise anyone could claim any
 * typo'd address and wait for its reset mail. Every public outcome is
 * generic: nothing reveals whether an address is registered.
 */
@Service
@Transactional
public class EmailVerificationService {

    static final Duration TTL = Duration.ofHours(24);
    static final String GENERIC_MESSAGE = "If an account exists for that address, a verification link is on its way.";

    private static final Logger log = LoggerFactory.getLogger(EmailVerificationService.class);

    private final UserRepository users;
    private final EmailVerificationTokenRepository tokens;
    private final AuthAttemptThrottle attempts;
    private final MailPort mail;
    private final String frontendUrl;

    public EmailVerificationService(
            UserRepository users,
            EmailVerificationTokenRepository tokens,
            AuthAttemptThrottle attempts,
            MailPort mail,
            @Value("${app.frontend.url:http://localhost:4200}") String frontendUrl) {
        this.users = users;
        this.tokens = tokens;
        this.attempts = attempts;
        this.mail = mail;
        this.frontendUrl = frontendUrl;
    }

    /** Mints and mails a verification link. No-op for already-verified accounts. */
    public void sendFor(User user) {
        if (user.isVerified()) {
            return;
        }
        String rawToken = AuthService.randomToken();
        tokens.save(new EmailVerificationToken(user, AuthService.hash(rawToken), Instant.now().plus(TTL)));
        String root = frontendUrl.replaceAll("/+$", "");
        String body = "Confirm your ProcureFlow mailbox to unlock password resets.\n\nVerify here (valid 24 hours,"
                + " single use):\n" + root + "/verify-email?token=" + rawToken + "\n\nIf that was not you, ignore"
                + " this email.";
        try {
            mail.send(new MailPort.OutgoingMail(user.getEmail(), "Verify your ProcureFlow mailbox", body));
        } catch (MailException e) {
            log.warn("Verification mail submission failed for recipient domain {}", domainOf(user.getEmail()));
        }
    }

    /** Resends to exactly one matching account; otherwise the generic answer. */
    public String resend(String email) {
        String normalized = email == null ? "" : email.trim().toLowerCase();
        attempts.check("verify:" + AuthService.hash(normalized), 5, Duration.ofHours(1));
        if (!normalized.contains("@")) {
            return GENERIC_MESSAGE;
        }
        List<User> candidates = users.findAllByEmail(normalized);
        if (candidates.size() != 1) {
            return GENERIC_MESSAGE;
        }
        sendFor(candidates.get(0));
        return GENERIC_MESSAGE;
    }

    /** Redeems a link: unknown tokens 404, spent or expired ones 410. */
    public void verify(String rawToken) {
        String hash = AuthService.hash(rawToken == null ? "" : rawToken);
        EmailVerificationToken token = tokens
                .findByTokenHash(hash)
                .orElseThrow(() -> ApiException.notFound("INVALID_TOKEN", "This verification link is invalid."));
        if (!token.isConsumable(Instant.now())) {
            throw ApiException.gone("TOKEN_SPENT", "This verification link has expired or was already used.");
        }
        if (tokens.consumeIfLive(hash, Instant.now()) != 1) {
            throw ApiException.gone("TOKEN_SPENT", "This verification link has expired or was already used.");
        }
        token.getUser().setVerified(true);
    }

    private static String domainOf(String address) {
        int at = address.lastIndexOf('@');
        return at >= 0 ? address.substring(at + 1) : "unknown";
    }
}
