package com.procureflow.identity.application;

import com.procureflow.shared.web.ApiException;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
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
    static final int INVITE_ISSUE_MAX_PER_ACTOR_PER_HOUR = 60;
    static final int INVITE_REDEEM_MAX_PER_IP_PER_HOUR = 30;
    static final int VERIFY_REDEEM_MAX_PER_IP_PER_HOUR = 60;
    static final int ADMIN_CREATE_MAX_PER_TENANT_PER_HOUR = 200;
    static final int CHANGE_PASSWORD_MAX_PER_ACCOUNT_PER_MINUTE = 10;

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
     * Invite issue: per-actor hourly budget. Admins mint seats, not tenants,
     * so the budget follows the actor rather than the source address.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void checkInviteIssue(UUID actorId) {
        check(
                "invite-issue:actor:" + AuthService.hash(String.valueOf(actorId)),
                INVITE_ISSUE_MAX_PER_ACTOR_PER_HOUR,
                Duration.ofHours(1));
    }

    /**
     * Invite redeem: per-IP hourly budget, deliberately tighter than
     * registration — joins are rarer than sign-ups, and this is the
     * unauthenticated token-guessing surface.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void checkInviteRedeem(String clientIp) {
        check(
                "invite-redeem:ip:" + AuthService.hash(normalizeIp(clientIp)),
                INVITE_REDEEM_MAX_PER_IP_PER_HOUR,
                Duration.ofHours(1));
    }

    /** Mailbox-verification redeem: per-IP hourly budget on the guessing surface. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void checkVerifyRedeem(String clientIp) {
        check(
                "verify-redeem:ip:" + AuthService.hash(normalizeIp(clientIp)),
                VERIFY_REDEEM_MAX_PER_IP_PER_HOUR,
                Duration.ofHours(1));
    }

    /**
     * Admin seat creation: per-tenant hourly flood guard. Roomy on purpose —
     * this damps bulk abuse, it does not second-guess legitimate provisioning.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void checkAdminCreate(String tenantSlug) {
        check(
                "admin-create:tenant:" + AuthService.hash(tenantSlug == null ? "" : tenantSlug),
                ADMIN_CREATE_MAX_PER_TENANT_PER_HOUR,
                Duration.ofHours(1));
    }

    /** Password change: per-account minute budget, mirroring login. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void checkChangePassword(UUID userId) {
        check(
                "change-password:account:" + AuthService.hash(String.valueOf(userId)),
                CHANGE_PASSWORD_MAX_PER_ACCOUNT_PER_MINUTE,
                Duration.ofMinutes(1));
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
