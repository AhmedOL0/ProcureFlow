package com.procureflow.purchaseorder.application;

import java.util.List;
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

    record LockedOrder(UUID orderId, String status, String currency, List<Line> lines) {
    }

    record Line(UUID id, int quantity, long unitPriceMinor, String currency, int receivedQty) {
    }
}
