package com.procureflow.supplier.application;

import java.util.UUID;

/**
 * Cross-module port: supplier existence checks for other modules (purchase
 * request items today). Primitives only, entities never cross modules.
 */
public interface SupplierLookup {

    boolean existsInTenant(UUID supplierId, String tenantSlug);
}
