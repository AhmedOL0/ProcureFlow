package com.procureflow.identity.application;

import com.procureflow.identity.domain.Permission;
import com.procureflow.identity.domain.Role;
import com.procureflow.identity.infrastructure.PermissionRepository;
import com.procureflow.identity.infrastructure.RoleRepository;
import com.procureflow.organization.domain.Tenant;
import com.procureflow.shared.web.ApiException;
import jakarta.persistence.EntityManager;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds the per-tenant role skeleton on first use: TENANT_ADMIN holds every
 * permission, OFFICER covers operational writes, MEMBER holds the safe
 * defaults. Custom roles arrive with the admin console.
 */
@Service
public class RoleProvisioningService {

    public static final String TENANT_ADMIN = "TENANT_ADMIN";
    public static final String OFFICER = "OFFICER";
    public static final String MEMBER = "MEMBER";

    private final RoleRepository roles;
    private final PermissionRepository permissions;
    private final EntityManager entities;

    public RoleProvisioningService(
            RoleRepository roles, PermissionRepository permissions, EntityManager entities) {
        this.roles = roles;
        this.permissions = permissions;
        this.entities = entities;
    }

    @Transactional
    public void ensureDefaultRoles(UUID tenantId) {
        List<Role> existing = roles.findAllByTenant_Id(tenantId);
        if (!existing.isEmpty()) {
            return;
        }
        Tenant tenant = entities.getReference(Tenant.class, tenantId);
        Role admin = new Role(tenant, TENANT_ADMIN);
        admin.setDescription("Full control over the tenant workspace");
        admin.getPermissions().addAll(permissions.findAllByCodeIn(PermissionCodes.ALL));
        roles.save(admin);
        Role member = new Role(tenant, MEMBER);
        member.setDescription("Request and read-only access");
        member.getPermissions().addAll(permissions.findAllByCodeIn(PermissionCodes.MEMBER_DEFAULTS));
        roles.save(member);
        Role officer = new Role(tenant, OFFICER);
        officer.setDescription("Procurement officers: suppliers, orders, invoices");
        officer.getPermissions().addAll(permissions.findAllByCodeIn(PermissionCodes.OFFICER_DEFAULTS));
        roles.save(officer);
    }

    @Transactional(readOnly = true)
    public Set<Role> resolveRoles(String tenantSlug, Set<String> roleNames) {
        Set<Role> result = new HashSet<>();
        for (String name : roleNames) {
            Role role = roles
                    .findByTenantSlugAndName(tenantSlug, name)
                    .orElseThrow(() -> ApiException.badRequest("UNKNOWN_ROLE", "Unknown role: " + name));
            result.add(role);
        }
        return result;
    }
}
