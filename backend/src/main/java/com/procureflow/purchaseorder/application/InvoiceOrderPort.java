package com.procureflow.purchaseorder.application;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Cross-module port: invoices read orders and lock them while booking
 * lines, so concurrent invoices on one order serialize. Primitives and
 * DTOs only; order entities never cross modules.
 */
public interface InvoiceOrderPort {

    /** Locks the order row (FOR UPDATE); 404 when absent or foreign to the tenant. */
    LockedOrder lockForInvoicing(String tenantSlug, UUID orderId);

    /** Read-only view of the order with its lines; 404 when absent or foreign. */
    LockedOrder orderView(String tenantSlug, UUID orderId);

    /** Bulk views for exports: foreign ids simply absent. */
    Map<UUID, LockedOrder> orderViews(String tenantSlug, Set<UUID> orderIds);

    record LockedOrder(UUID orderId, UUID supplierId, String status, String currency, List<Line> lines) {
    }

    record Line(UUID id, String description, int quantity, long unitPriceMinor, String currency, int receivedQty) {
    }
}
