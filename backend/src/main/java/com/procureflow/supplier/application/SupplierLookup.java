package com.procureflow.supplier.application;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Cross-module port: supplier existence checks for other modules (purchase
 * request items today). Cards carry display fields for documents —
 * primitives and records only, entities never cross modules.
 */
public interface SupplierLookup {

    boolean existsInTenant(UUID supplierId, String tenantSlug);

    /** Display card (name + contact) for documents, or empty cross-tenant. */
    Optional<Card> cardInTenant(UUID supplierId, String tenantSlug);

    /** Bulk cards for exports: one query, missing ids simply absent. */
    Map<UUID, Card> cardsInTenant(Set<UUID> supplierIds, String tenantSlug);

    /** Display fields of a supplier for cross-module documents. */
    record Card(UUID id, String name, String email, String address) {
    }
}
