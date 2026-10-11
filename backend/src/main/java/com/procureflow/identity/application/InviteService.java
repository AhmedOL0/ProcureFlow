package com.procureflow.identity.application;

import com.procureflow.identity.domain.Invite;
import com.procureflow.identity.domain.User;
import com.procureflow.identity.infrastructure.InviteRepository;
import com.procureflow.identity.infrastructure.UserRepository;
import com.procureflow.organization.application.TenantProvisioning;
import com.procureflow.shared.web.ApiException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Workspace invites: the only path into an existing tenant. An admin names
 * an address and a role set; the invite link carries a single-use token and
 * redemption assigns exactly those roles. The raw token travels once inside
 * the mailed link — the invite response never contains it.
 */
@Service
@Transactional
public class InviteService {

    private static final Duration TTL = Duration.ofDays(7);

    private static final Logger log = LoggerFactory.getLogger(InviteService.class);

    private final InviteRepository invites;
    private final UserRepository users;
    private final TenantProvisioning tenants;
    private final RoleProvisioningService roleProvisioning;
    private final MailPort mail;
    private final String frontendUrl;

    public InviteService(
            InviteRepository invites,
            UserRepository users,
            TenantProvisioning tenants,
            RoleProvisioningService roleProvisioning,
            MailPort mail,
            @Value("${app.frontend.url:http://localhost:4200}") String frontendUrl) {
        this.invites = invites;
        this.users = users;
        this.tenants = tenants;
        this.roleProvisioning = roleProvisioning;
        this.mail = mail;
        this.frontendUrl = frontendUrl;
    }

    public Invite invite(String tenantSlug, UUID actorId, String email, Set<String> roleNames) {
        UUID tenantId = tenants.requireTenantId(tenantSlug);
        String normalized = email == null ? "" : email.trim().toLowerCase();
        if (!normalized.contains("@")) {
            throw ApiException.badRequest("INVALID_EMAIL", "A valid email address is required");
        }
        if (roleNames == null || roleNames.isEmpty()) {
            throw ApiException.badRequest("EMPTY_ROLES", "Assign at least one role");
        }
        // Unknown names fail here, before anything is stored or mailed.
        roleProvisioning.resolveRoles(tenantSlug, roleNames);
        User actor = users.findById(actorId)
                .orElseThrow(() -> ApiException.unauthorized("INVALID_CREDENTIALS", "Invalid email or password"));
        String rawToken = AuthService.randomToken();
        Invite invite = invites.save(new Invite(
                tenantId, normalized, String.join(",", roleNames.stream().sorted().toList()),
                AuthService.hash(rawToken), Instant.now().plus(TTL), actor));
        mailInvite(normalized, rawToken);
        return invite;
    }

    @Transactional(readOnly = true)
    public List<Invite> list(String tenantSlug) {
        return invites.findAllByTenantIdOrderByCreatedAtDesc(tenants.requireTenantId(tenantSlug));
    }

    public void revoke(String tenantSlug, UUID inviteId) {
        Invite invite = invites
                .findById(inviteId)
                .filter(row -> row.getTenantId().equals(tenants.requireTenantId(tenantSlug)))
                .orElseThrow(() -> ApiException.notFound("INVITE_NOT_FOUND", "Invite not found"));
        invites.delete(invite);
    }

    /**
     * Redeems a live invite for an address: exactly one winner per token
     * under concurrency. Returns the invite's role names.
     */
    public Set<String> redeem(String tenantSlug, String email, String rawToken) {
        String hash = AuthService.hash(rawToken == null ? "" : rawToken);
        UUID tenantId = tenants.requireTenantId(tenantSlug);
        String normalized = email == null ? "" : email.trim().toLowerCase();
        Invite live = invites.findLiveByTokenHash(hash, Instant.now()).stream()
                .filter(row -> row.getTenantId().equals(tenantId) && row.getEmail().equals(normalized))
                .findFirst()
                .orElseThrow(() -> ApiException.forbidden("INVITE_REQUIRED", "A valid invite is required to join"));
        if (invites.consumeIfLive(live.getId(), Instant.now()) != 1) {
            throw ApiException.forbidden("INVITE_REQUIRED", "A valid invite is required to join");
        }
        return Set.of(live.getRoles().split(","));
    }

    private void mailInvite(String to, String rawToken) {
        String root = frontendUrl.replaceAll("/+$", "");
        String body = "You are invited to join a ProcureFlow workspace.\n\nAccept here (valid 7 days, single use):\n"
                + root + "/register?invite=" + rawToken + "\n\nIf that was not you, ignore this email.";
        try {
            mail.send(new MailPort.OutgoingMail(to, "You are invited to ProcureFlow", body));
        } catch (MailException e) {
            log.warn("Invite mail submission failed for recipient domain {}", domainOf(to));
        }
    }

    private static String domainOf(String address) {
        int at = address.lastIndexOf('@');
        return at >= 0 ? address.substring(at + 1) : "unknown";
    }
}
