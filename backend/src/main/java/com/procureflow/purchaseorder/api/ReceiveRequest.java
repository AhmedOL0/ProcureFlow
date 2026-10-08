package com.procureflow.purchaseorder.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

/** Record goods receipt: one or more order lines with received quantities. */
public record ReceiveRequest(
        @NotEmpty List<@Valid ReceiptLine> lines) {

    public record ReceiptLine(
            @NotNull UUID itemId,
            @Min(1) int quantity) {
    }
}
