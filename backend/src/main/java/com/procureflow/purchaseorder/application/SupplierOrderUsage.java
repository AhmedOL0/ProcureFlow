package com.procureflow.purchaseorder.application;

import java.util.UUID;

/**
 * Cross-module port: the supplier reference guard. Supplier deletes consult
 * this before removing; primitives only.
 */
public interface SupplierOrderUsage {

    /** True when any order of the tenant points at the supplier. */
    boolean isReferenced(String tenantSlug, UUID supplierId);
}
