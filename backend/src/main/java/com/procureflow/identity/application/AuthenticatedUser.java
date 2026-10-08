package com.procureflow.identity.application;

import java.util.Set;
import java.util.UUID;

/**
 * The authenticated caller as carried by the JWT and the security context.
 * Tenant id is the tenant slug (immutable once created).
 */
public record AuthenticatedUser(
        UUID userId,
        String tenantId,
        String email,
        Set<String> authorities) {
}
