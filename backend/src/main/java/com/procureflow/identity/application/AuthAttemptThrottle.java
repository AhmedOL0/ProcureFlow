package com.procureflow.identity.application;

import com.procureflow.shared.web.ApiException;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Rate limits for the public authentication endpoints. Buckets are hashed
 * (SHA-256 of the email or client IP) so the ledger holds counts, never
 * credential or address material. Limits are deliberately modest baselines,
 * not edge protection: per-account login is the real control against
 * credential stuffing, per-IP buckets damp floods. A stricter edge (WAF,
 * fail2ban) still belongs in front of any exposed deployment.
 */
@Component
public class AuthAttemptThrottle {

    static final int LOGIN_MAX_PER_ACCOUNT_PER_MINUTE = 10;
    static final int LOGIN_MAX_PER_IP_PER_HOUR = 120;
    static final int REGISTER_MAX_PER_IP_PER_HOUR = 60;
    static final int REFRESH_MAX_PER_IP_PER_HOUR = 600;

    private final AttemptLedger ledger;

    public AuthAttemptThrottle(AttemptLedger ledger) {
        this.ledger = ledger;
    }

    /**
     * Login: per-account minute budget plus a looser per-IP hourly budget.
     * Isolated transaction: the caller's login usually fails (401) and rolls
     * its transaction back, which must not erase the attempt record —
     * otherwise stuffing attempts would leave no trace.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void checkLogin(String email, String clientIp) {
        String account = (email == null ? "" : email.trim().toLowerCase());
        check("login:account:" + AuthService.hash(account), LOGIN_MAX_PER_ACCOUNT_PER_MINUTE, Duration.ofMinutes(1));
        check("login:ip:" + AuthService.hash(normalizeIp(clientIp)), LOGIN_MAX_PER_IP_PER_HOUR, Duration.ofHours(1));
    }

    /** Registration: per-IP hourly budget (every registration mints a tenant or a seat). */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void checkRegister(String clientIp) {
        check(
                "register:ip:" + AuthService.hash(normalizeIp(clientIp)),
                REGISTER_MAX_PER_IP_PER_HOUR,
                Duration.ofHours(1));
    }

    /** Refresh rotation: generous per-IP hourly budget; theft detection remains the real control. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void checkRefresh(String clientIp) {
        check(
                "refresh:ip:" + AuthService.hash(normalizeIp(clientIp)),
                REFRESH_MAX_PER_IP_PER_HOUR,
                Duration.ofHours(1));
    }

    /**
     * Generic budget check shared by every throttled flow (auth endpoints
     * and password reset): prune, record, count, 429 past the max. One
     * policy, one ledger; buckets namespace the flows apart.
     */
    public void check(String bucket, int max, Duration window) {
        Instant now = Instant.now();
        ledger.pruneBefore(now.minus(window.multipliedBy(2)));
        ledger.record(bucket, now);
        if (ledger.countSince(bucket, now.minus(window)) > max) {
            throw ApiException.tooManyRequests("TOO_MANY_REQUESTS", "Too many attempts; try again later.");
        }
    }

    private static String normalizeIp(String clientIp) {
        return clientIp == null || clientIp.isBlank() ? "unknown" : clientIp.trim();
    }
}
