package com.procureflow.procurement.application;

import java.util.List;
import java.util.UUID;

/**
 * Cross-module port: create a purchase-request draft from another module's
 * validated data (AI extraction drafts today). The caller enforces its own
 * authorities at its edge; this port runs the same idempotent creation the
 * REST endpoint uses, key included. Plain values only; entities and web
 * types never cross modules.
 */
public interface RequestDrafting {

    Creation createFromDraft(String tenantSlug, UUID requesterId, String key, String title, String priority,
            List<ItemInput> items);

    /** requestId of the created (or replayed) draft; replayed mirrors X-Replay. */
    record Creation(UUID requestId, boolean replayed) {
    }

    /** Draft line as the extractor produced it; category/currency stay unset. */
    record ItemInput(String description, int quantity, long unitPriceMinor) {
    }
}
