package com.procureflow.purchaseorder.api;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** Start a supplier order from one approved request. Lines are snapshotted. */
public record CreateOrderRequest(
        @NotNull UUID requestId,
        @NotNull UUID supplierId) {
}
