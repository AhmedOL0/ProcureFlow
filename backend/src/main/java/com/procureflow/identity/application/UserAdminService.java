package com.procureflow.identity.application;

import com.procureflow.audit.application.AuditTrailLogged;
import com.procureflow.identity.domain.Role;
import com.procureflow.identity.domain.User;
import com.procureflow.identity.infrastructure.RefreshTokenRepository;
import com.procureflow.identity.infrastructure.UserRepository;
import com.procureflow.shared.web.ApiException;
import com.procureflow.shared.web.Paged;
import com.procureflow.organization.application.TenantProvisioning;
import com.procureflow.organization.domain.Tenant;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tenant-scoped user administration (tenant admins only, enforced at the
 * controller). Reads and creates users inside the caller's own workspace;
 * cross-tenant access degrades to 404, never 403, to avoid leaking
 * the existence of other tenants' users.
 */
@Service
@Transactional
public class UserAdminService {

    private final UserRepository users;
    private final RoleProvisioningService roleProvisioning;
    private final TenantProvisioning tenants;
    private final PasswordEncoder encoder;
    private final RefreshTokenRepository refreshTokens;
    private final EntityManager entities;
    private final ApplicationEventPublisher events;
    private final EmailVerificationService verification;

    public UserAdminService(
            UserRepository users,
            RoleProvisioningService roleProvisioning,
            TenantProvisioning tenants,
            PasswordEncoder encoder,
            RefreshTokenRepository refreshTokens,
            EntityManager entities,
            ApplicationEventPublisher events,
            EmailVerificationService verification) {
        this.users = users;
        this.roleProvisioning = roleProvisioning;
        this.tenants = tenants;
        this.encoder = encoder;
        this.refreshTokens = refreshTokens;
        this.entities = entities;
        this.events = events;
        this.verification = verification;
    }

    @Transactional(readOnly = true)
    public Paged<User> page(String tenantSlug, String query, int page, int size) {
        String terms = query == null || query.isBlank() ? "" : query;
        Page<User> found = users.findPageByTenantSlug(tenantSlug, terms, PageRequest.of(page, size));
        return Paged.of(found.getContent(), page, size, found.getTotalElements());
    }

    @Transactional(readOnly = true)
    public User get(String tenantSlug, UUID userId) {
        return users
                .findDetailedById(userId)
                .filter(user -> user.getTenant().getSlug().equals(tenantSlug))
                .orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "User not found"));
    }

    public User create(
            String tenantSlug,
            String email,
            String password,
            String firstName,
            String lastName,
            Set<String> roleNames) {
        PasswordPolicy.requireValid(password);
        String normalized = email.trim().toLowerCase();
        if (users.findByTenantSlugAndEmail(tenantSlug, normalized).isPresent()) {
            throw ApiException.conflict("EMAIL_IN_USE", "This email is already registered in this workspace");
        }
        UUID tenantId = tenants.requireTenantId(tenantSlug);
        User user = new User(
                entities.getReference(Tenant.class, tenantId), normalized, encoder.encode(password));
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.getRoles().addAll(roleProvisioning.resolveRoles(tenantSlug, roleNames));
        users.save(user);
        verification.sendFor(user);
        return user;
    }

    /**
     * Updates a workspace profile. Null fields stay unchanged; email, roles
     * and status are intentionally not editable here (identity moves through
     * registration, roles through the admin console's next slice, and there
     * is no deactivation concept to expose).
     */
    public User updateProfile(String tenantSlug, UUID userId, String firstName, String lastName) {
        User user = get(tenantSlug, userId);
        if (firstName != null) {
            user.setFirstName(trimOrNull(firstName));
        }
        if (lastName != null) {
            user.setLastName(trimOrNull(lastName));
        }
        return users.save(user);
    }

    /**
     * Helpdesk-style reset: a workspace admin sets a new password. Every
     * session of the target ends here — all refresh tokens are revoked so a
     * compromised password stops working everywhere at once.
     */
    public void resetPassword(String tenantSlug, UUID userId, String newPassword) {
        PasswordPolicy.requireValid(newPassword);
        User user = get(tenantSlug, userId);
        user.setPasswordHash(encoder.encode(newPassword));
        users.save(user);
        revokeAllSessions(user.getId());
    }

    /**
     * Suspends (DISABLED) or reactivates (ACTIVE) a workspace user. A
     * disabled account fails login and refresh; its sessions are revoked up
     * front, so only a live access token (≤15 min) can outlast the call.
     * Self-suspension is refused — a tenant must always keep an admin able
     * to act. INVITED is not an assignable state (no invite flow yet).
     */
    public User setStatus(String tenantSlug, UUID actorId, UUID userId, User.Status status) {
        if (status != User.Status.ACTIVE && status != User.Status.DISABLED) {
            throw ApiException.badRequest("INVALID_STATUS", "Status must be ACTIVE or DISABLED");
        }
        User user = get(tenantSlug, userId);
        if (user.getId().equals(actorId)) {
            throw ApiException.conflict("CANNOT_SUSPEND_SELF", "You cannot change your own status");
        }
        User.Status before = user.getStatus();
        user.setStatus(status);
        users.save(user);
        if (status == User.Status.DISABLED) {
            revokeAllSessions(user.getId());
        }
        events.publishEvent(AuditTrailLogged.now(
                tenantSlug,
                actorId,
                "USER_STATUS_CHANGED",
                "user",
                user.getId(),
                Map.of("status", before.name()),
                Map.of("status", status.name())));
        return user;
    }

    /**
     * Replaces a workspace user's roles. Unknown names 400 via the same
     * resolver as creation; an empty set is refused so demotion always
     * lands on an explicit role. Self-change is refused — otherwise an
     * admin could lock every admin (including itself) out of management.
     */
    public User setRoles(String tenantSlug, UUID actorId, UUID userId, Set<String> roleNames) {
        if (roleNames == null || roleNames.isEmpty()) {
            throw ApiException.badRequest("EMPTY_ROLES", "Assign at least one role");
        }
        User user = get(tenantSlug, userId);
        if (user.getId().equals(actorId)) {
            throw ApiException.conflict("CANNOT_CHANGE_OWN_ROLES", "You cannot change your own roles");
        }
        Set<String> before =
                user.getRoles().stream().map(Role::getName).sorted().collect(Collectors.toSet());
        user.getRoles().clear();
        user.getRoles().addAll(roleProvisioning.resolveRoles(tenantSlug, roleNames));
        users.save(user);
        events.publishEvent(AuditTrailLogged.now(
                tenantSlug,
                actorId,
                "USER_ROLES_CHANGED",
                "user",
                user.getId(),
                Map.of("roles", String.join(",", before)),
                Map.of("roles", String.join(",", roleNames.stream().sorted().toList()))));
        return user;
    }

    private void revokeAllSessions(UUID userId) {
        refreshTokens.findAllByUser_IdAndRevokedFalse(userId).forEach(token -> token.revoke(null));
    }

    private static String trimOrNull(String value) {
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
