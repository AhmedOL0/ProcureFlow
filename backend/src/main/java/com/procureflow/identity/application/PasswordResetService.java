package com.procureflow.identity.application;

import com.procureflow.identity.domain.PasswordResetToken;
import com.procureflow.identity.domain.User;
import com.procureflow.identity.infrastructure.PasswordResetTokenRepository;
import com.procureflow.identity.infrastructure.RefreshTokenRepository;
import com.procureflow.identity.infrastructure.UserRepository;
import com.procureflow.shared.web.ApiException;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Email-link password resets. Tokens are cryptographically random,
 * single-use, short-lived, and stored hashed; the raw value travels once,
 * inside the emailed URL. Every public outcome is the same generic message
 * whether or not the account exists — enumeration resistance is a property
 * of the responses, not of good intentions.
 */
@Service
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);

    static final int FORGOT_MAX_PER_HOUR = 5;
    static final int RESET_MAX_PER_10_MINUTES = 10;
    static final String GENERIC_MESSAGE = "If an account exists for that address, a reset link is on its way.";

    private final UserRepository users;
    private final PasswordResetTokenRepository tokens;
    private final AuthAttemptThrottle attempts;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder encoder;
    private final MailPort mail;
    private final String frontendUrl;
    private final Duration tokenTtl;

    public PasswordResetService(
            UserRepository users,
            PasswordResetTokenRepository tokens,
            AuthAttemptThrottle attempts,
            RefreshTokenRepository refreshTokens,
            PasswordEncoder encoder,
            MailPort mail,
            @Value("${app.frontend.url:http://localhost:4200}") String frontendUrl,
            @Value("${app.auth.password-reset-ttl-minutes:30}") long ttlMinutes) {
        this.users = users;
        this.tokens = tokens;
        this.attempts = attempts;
        this.refreshTokens = refreshTokens;
        this.encoder = encoder;
        this.mail = mail;
        this.frontendUrl = frontendUrl;
        this.tokenTtl = Duration.ofMinutes(ttlMinutes);
    }

    /**
     * Starts a reset. Always succeeds from the caller's perspective; the
     * token is minted and mailed only when exactly one workspace account
     * matches (email alone is ambiguous across tenants, and guessing wrong
     * would touch another tenant's user).
     */
    @Transactional
    public String requestReset(String email, String tenantSlug) {
        String normalized = email == null ? "" : email.trim().toLowerCase();
        String slug = tenantSlug == null || tenantSlug.isBlank() ? null : tenantSlug.trim().toLowerCase();
        attempts.check(emailBucket(normalized, slug), FORGOT_MAX_PER_HOUR, Duration.ofHours(1));
        if (!normalized.contains("@")) {
            return GENERIC_MESSAGE;
        }
        List<User> candidates = slug == null
                ? users.findAllByEmail(normalized)
                : users.findByTenantSlugAndEmail(slug, normalized).map(List::of).orElseGet(List::of);
        if (candidates.size() != 1) {
            return GENERIC_MESSAGE;
        }
        User user = candidates.get(0);
        if (user.getStatus() != User.Status.ACTIVE) {
            // Activation is a separate concern; a dormant account gets no token.
            return GENERIC_MESSAGE;
        }
        if (!user.isVerified()) {
            // Mailbox ownership unproven: minting here would let anyone claim
            // a typo'd address and wait for its reset mail. Generic reply.
            return GENERIC_MESSAGE;
        }
        String rawToken = AuthService.randomToken();
        tokens.save(new PasswordResetToken(user, AuthService.hash(rawToken), Instant.now().plus(tokenTtl)));
        mailResetLink(user.getEmail(), resetUrl(rawToken));
        return GENERIC_MESSAGE;
    }

    /**
     * Redeems a reset token for a new password. Unknown tokens 404, spent or
     * expired ones 410, brute-forced ones 429 — all without saying which
     * account (if any) the token belonged to. Success revokes every session.
     */
    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        String hash = AuthService.hash(rawToken == null ? "" : rawToken);
        attempts.check("token:" + hash, RESET_MAX_PER_10_MINUTES, Duration.ofMinutes(10));
        PasswordResetToken token = tokens
                .findByTokenHash(hash)
                .orElseThrow(() -> ApiException.notFound("INVALID_TOKEN", "This reset link is invalid."));
        if (!token.isConsumable(Instant.now())) {
            throw ApiException.gone("TOKEN_SPENT", "This reset link has expired or was already used.");
        }
        PasswordPolicy.requireValid(newPassword);
        int consumed = tokens.consumeIfLive(hash, Instant.now());
        if (consumed != 1) {
            throw ApiException.gone("TOKEN_SPENT", "This reset link has expired or was already used.");
        }
        User user = token.getUser();
        user.setPasswordHash(encoder.encode(newPassword));
        refreshTokens.findAllByUser_IdAndRevokedFalse(user.getId()).forEach(stale -> stale.revoke(null));
    }

    private void mailResetLink(String to, String resetUrl) {
        String body = "Someone requested a password reset for this address in ProcureFlow.\n\n"
                + "Choose a new password here (valid "
                + tokenTtl.toMinutes()
                + " minutes, single use):\n"
                + resetUrl
                + "\n\nIf that was not you, ignore this email — your password stays unchanged.";
        try {
            mail.send(new MailPort.OutgoingMail(to, "Reset your ProcureFlow password", body));
        } catch (MailException e) {
            // Generic response already promised; delivery failure is an ops
            // incident, and bodies carry reset URLs that must never be logged.
            log.warn("Password-reset mail submission failed for recipient domain {}", domainOf(to));
        }
    }

    private String resetUrl(String rawToken) {
        URI base;
        try {
            base = URI.create(frontendUrl);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("app.frontend.url is misconfigured", e);
        }
        if (!("http".equalsIgnoreCase(base.getScheme()) || "https".equalsIgnoreCase(base.getScheme()))
                || base.getHost() == null) {
            throw new IllegalStateException("app.frontend.url must be an absolute http(s) URL");
        }
        String root = base.toString().replaceAll("/+$", "");
        return root + "/reset-password?token=" + rawToken;
    }

    private static String emailBucket(String email, String tenantSlug) {
        String scope = tenantSlug == null ? email : email + "|" + tenantSlug;
        return "email:" + AuthService.hash(scope);
    }

    private static String domainOf(String address) {
        int at = address.lastIndexOf('@');
        return at >= 0 ? address.substring(at + 1) : "unknown";
    }
}
