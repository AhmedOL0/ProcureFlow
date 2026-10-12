package com.procureflow.identity.application;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Cross-module port: tenant-scoped user existence checks for other modules
 * (membership validation today, approval assignment tomorrow). Cards carry
 * display fields only — entities never cross modules.
 */
public interface UserLookup {

    boolean existsInTenant(UUID userId, String tenantSlug);

    /** Display card (email + name) for documents, or empty cross-tenant. */
    Optional<Card> cardInTenant(UUID userId, String tenantSlug);

    /** Bulk cards for exports: one query, missing ids simply absent. */
    Map<UUID, Card> cardsInTenant(Set<UUID> userIds, String tenantSlug);

    /** Display fields of a workspace user for cross-module documents. */
    record Card(UUID id, String email, String firstName, String lastName) {

        public String displayName() {
            String full =
                    ((firstName == null ? "" : firstName) + " " + (lastName == null ? "" : lastName)).trim();
            return full.isEmpty() ? (email == null ? "" : email) : full;
        }
    }
}
