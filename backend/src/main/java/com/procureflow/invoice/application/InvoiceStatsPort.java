package com.procureflow.invoice.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Cross-module read port: invoiced and paid volumes for analytics.
 * Plain values only; entities never cross modules.
 */
public interface InvoiceStatsPort {

    List<InvoiceStat> invoiceStats(String tenantSlug);

    /** Month-bounded variant: half-open [from, to), never null at the call site. */
    List<InvoiceStat> invoiceStats(String tenantSlug, Instant from, Instant to);

    record InvoiceStat(UUID invoiceId, String status, long totalMinor, String currency, Instant createdAt,
            long paidMinor) {
    }

    /** DB-side totals for the all-time path: stamps and sums, no line entities. */
    record InvoiceTotal(UUID invoiceId, String status, Instant createdAt, long totalMinor, long paidMinor) {
    }

    List<InvoiceTotal> invoiceTotals(String tenantSlug);
}
