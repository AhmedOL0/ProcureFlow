package com.procureflow.identity.application;

import com.procureflow.identity.domain.Permission;
import com.procureflow.identity.domain.Role;
import com.procureflow.identity.infrastructure.PermissionRepository;
import com.procureflow.identity.infrastructure.RoleRepository;
import com.procureflow.organization.domain.Tenant;
import com.procureflow.shared.web.ApiException;
import jakarta.persistence.EntityManager;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds the per-tenant role skeleton on first use: TENANT_ADMIN holds every
 * permission, OFFICER covers operational writes, APPROVER decides with
 * budget context, FINANCE owns pots and payables, AUDITOR reads the trail,
 * MEMBER holds the safe defaults. Custom roles arrive with the admin console.
 */
@Service
public class RoleProvisioningService {

    public static final String TENANT_ADMIN = "TENANT_ADMIN";
    public static final String OFFICER = "OFFICER";
    public static final String MEMBER = "MEMBER";
    public static final String APPROVER = "APPROVER";
    public static final String FINANCE = "FINANCE";
    public static final String AUDITOR = "AUDITOR";

    private final RoleRepository roles;
    private final PermissionRepository permissions;
    private final EntityManager entities;

    public RoleProvisioningService(
            RoleRepository roles, PermissionRepository permissions, EntityManager entities) {
        this.roles = roles;
        this.permissions = permissions;
        this.entities = entities;
    }

    /**
     * Seeds every missing default role. Additive by design: tenants created
     * before a role existed get it on next provisioning without touching
     * the roles (or their grants) they already have.
     */
    @Transactional
    public void ensureDefaultRoles(UUID tenantId) {
        Set<String> existing = new HashSet<>();
        for (Role role : roles.findAllByTenant_Id(tenantId)) {
            existing.add(role.getName());
        }
        Tenant tenant = entities.getReference(Tenant.class, tenantId);
        seedRole(tenant, existing, TENANT_ADMIN, "Full control over the tenant workspace",
                PermissionCodes.ALL);
        seedRole(tenant, existing, MEMBER, "Request and read-only access",
                PermissionCodes.MEMBER_DEFAULTS);
        seedRole(tenant, existing, OFFICER, "Procurement officers: suppliers, orders, invoices",
                PermissionCodes.OFFICER_DEFAULTS);
        seedRole(tenant, existing, APPROVER, "Approvers: decide requests with budget context",
                PermissionCodes.APPROVER_DEFAULTS);
        seedRole(tenant, existing, FINANCE, "Finance: budgets, invoices and spend oversight",
                PermissionCodes.FINANCE_DEFAULTS);
        seedRole(tenant, existing, AUDITOR, "Auditors: read-only trail and analytics",
                PermissionCodes.AUDITOR_DEFAULTS);
    }

    private void seedRole(
            Tenant tenant, Set<String> existing, String name, String description, Set<String> permissionCodes) {
        if (existing.contains(name)) {
            return;
        }
        Role role = new Role(tenant, name);
        role.setDescription(description);
        role.getPermissions().addAll(permissions.findAllByCodeIn(permissionCodes));
        roles.save(role);
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
