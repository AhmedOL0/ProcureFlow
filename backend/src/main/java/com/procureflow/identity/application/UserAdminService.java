package com.procureflow.identity.application;

import com.procureflow.identity.domain.User;
import com.procureflow.identity.infrastructure.RefreshTokenRepository;
import com.procureflow.identity.infrastructure.UserRepository;
import com.procureflow.shared.web.ApiException;
import com.procureflow.organization.application.TenantProvisioning;
import com.procureflow.organization.domain.Tenant;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
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

    public UserAdminService(
            UserRepository users,
            RoleProvisioningService roleProvisioning,
            TenantProvisioning tenants,
            PasswordEncoder encoder,
            RefreshTokenRepository refreshTokens,
            EntityManager entities) {
        this.users = users;
        this.roleProvisioning = roleProvisioning;
        this.tenants = tenants;
        this.encoder = encoder;
        this.refreshTokens = refreshTokens;
        this.entities = entities;
    }

    @Transactional(readOnly = true)
    public List<User> list(String tenantSlug) {
        return users.findAllByTenantSlug(tenantSlug);
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
        return users.save(user);
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

    private void revokeAllSessions(UUID userId) {
        refreshTokens.findAllByUser_IdAndRevokedFalse(userId).forEach(token -> token.revoke(null));
    }

    private static String trimOrNull(String value) {
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
