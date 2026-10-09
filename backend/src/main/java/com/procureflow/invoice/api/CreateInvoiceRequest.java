package com.procureflow.invoice.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

/** Book an invoice against a sent order's lines. */
public record CreateInvoiceRequest(
        @NotNull UUID orderId,
        @NotNull String number,
        @NotEmpty List<@Valid InvoiceLineInput> lines) {

    public record InvoiceLineInput(
            @NotNull UUID orderItemId,
            @Min(1) int quantity) {
    }
}
