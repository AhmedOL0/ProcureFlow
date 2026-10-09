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

    record InvoiceStat(UUID invoiceId, String status, long totalMinor, String currency, Instant createdAt,
            long paidMinor) {
    }
}
