package com.procureflow.identity.application;

import com.procureflow.identity.domain.User;
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
    private final EntityManager entities;

    public UserAdminService(
            UserRepository users,
            RoleProvisioningService roleProvisioning,
            TenantProvisioning tenants,
            PasswordEncoder encoder,
            EntityManager entities) {
        this.users = users;
        this.roleProvisioning = roleProvisioning;
        this.tenants = tenants;
        this.encoder = encoder;
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
}
