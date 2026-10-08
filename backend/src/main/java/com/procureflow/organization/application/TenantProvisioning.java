package com.procureflow.organization.application;

import java.util.UUID;

/**
 * Cross-module port: tenant lifecycle for other modules (registration
 * creates-or-joins a tenant today). Identifiers travel as primitives;
 * entities never cross module boundaries.
 */
public interface TenantProvisioning {

    /** Creates the tenant (plus default roles) or returns the existing id. */
    UUID ensureTenant(String slug, String name);

    /** Existing tenant id, or 404. */
    UUID requireTenantId(String slug);

    boolean exists(String slug);
}
