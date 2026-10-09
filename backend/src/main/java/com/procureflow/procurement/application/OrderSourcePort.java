package com.procureflow.procurement.application;

import java.util.List;
import java.util.UUID;

/**
 * Cross-module port: purchase orders are born from approved requests. The
 * snapshot carries plain values for the order module to copy; the request
 * entity never crosses modules.
 */
public interface OrderSourcePort {

    /** The request with its lines; 404 when absent or foreign to the tenant. */
    Snapshot snapshot(String tenantSlug, UUID requestId);

    /** Marks an APPROVED request ORDERED; 409 from any other status. */
    void markOrdered(String tenantSlug, UUID requestId);

    record Snapshot(UUID requestId, String status, String currency, List<Line> lines) {
    }

    record Line(
            String description,
            String category,
            int quantity,
            long unitPriceMinor,
            String currency,
            UUID supplierId) {
    }
}
