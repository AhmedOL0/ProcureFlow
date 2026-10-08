package com.procureflow.organization.application;

import com.procureflow.identity.application.RoleProvisioningService;
import com.procureflow.organization.domain.Tenant;
import com.procureflow.organization.infrastructure.TenantRepository;
import com.procureflow.shared.web.ApiException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tenant lifecycle: provisioning (with default role skeleton) plus the
 * self-service reads/renames exposed to tenant admins.
 */
@Service
@Transactional
public class TenantService implements TenantProvisioning {

    private final TenantRepository tenants;
    private final RoleProvisioningService roleProvisioning;

    public TenantService(TenantRepository tenants, RoleProvisioningService roleProvisioning) {
        this.tenants = tenants;
        this.roleProvisioning = roleProvisioning;
    }

    @Override
    public UUID ensureTenant(String slug, String name) {
        return tenants
                .findBySlug(slug)
                .map(Tenant::getId)
                .orElseGet(() -> {
                    Tenant created = tenants.save(new Tenant(name, slug));
                    roleProvisioning.ensureDefaultRoles(created.getId());
                    return created.getId();
                });
    }

    @Override
    @Transactional(readOnly = true)
    public UUID requireTenantId(String slug) {
        return tenants
                .findBySlug(slug)
                .map(Tenant::getId)
                .orElseThrow(() -> ApiException.notFound("TENANT_NOT_FOUND", "Workspace not found"));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean exists(String slug) {
        return tenants.existsBySlug(slug);
    }

    @Transactional(readOnly = true)
    public Tenant current(String slug) {
        return tenants
                .findBySlug(slug)
                .orElseThrow(() -> ApiException.notFound("TENANT_NOT_FOUND", "Workspace not found"));
    }

    public Tenant rename(String slug, String name) {
        Tenant tenant = current(slug);
        tenant.setName(name);
        return tenants.save(tenant);
    }
}
