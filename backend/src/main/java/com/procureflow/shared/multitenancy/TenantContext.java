package com.procureflow.shared.multitenancy;

import java.util.Optional;

/**
 * Carries the current tenant for the executing thread. Phase 2 populates it
 * from the authenticated JWT in a servlet filter (and clears it afterwards);
 * data access then scopes every query to this tenant. Framework-free on
 * purpose so domain code can reference it without importing Spring.
 */
public final class TenantContext {

    private static final ThreadLocal<String> CURRENT_TENANT = new ThreadLocal<>();

    private TenantContext() {
        // utility class
    }

    public static void setTenantId(String tenantId) {
        CURRENT_TENANT.set(tenantId);
    }

    public static Optional<String> getTenantId() {
        return Optional.ofNullable(CURRENT_TENANT.get());
    }

    public static void clear() {
        CURRENT_TENANT.remove();
    }
}
