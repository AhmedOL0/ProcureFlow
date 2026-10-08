package com.procureflow.identity.application;

import java.util.UUID;

/**
 * Cross-module port: tenant-scoped user existence checks for other modules
 * (membership validation today, approval assignment tomorrow).
 */
public interface UserLookup {

    boolean existsInTenant(UUID userId, String tenantSlug);
}
