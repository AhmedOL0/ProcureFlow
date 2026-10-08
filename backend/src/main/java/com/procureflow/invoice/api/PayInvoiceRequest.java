package com.procureflow.invoice.api;

import jakarta.validation.constraints.Min;

/** Record a payment against an invoice; overpaying is rejected. */
public record PayInvoiceRequest(
        @Min(1) long amountMinor) {
}
