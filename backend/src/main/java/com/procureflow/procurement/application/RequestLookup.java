package com.procureflow.procurement.application;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Cross-module port: document titles for orders and exports. Records only;
 * the request entity never crosses modules.
 */
public interface RequestLookup {

    /** Title and status for documents, or empty when absent or foreign. */
    Optional<Card> cardInTenant(UUID requestId, String tenantSlug);

    /** Bulk cards for exports: one query, missing ids simply absent. */
    Map<UUID, Card> cardsInTenant(Set<UUID> requestIds, String tenantSlug);

    /** Display fields of a purchase request for cross-module documents. */
    record Card(UUID id, String title, String status) {
    }
}
