package com.procureflow.procurement.api;

import com.procureflow.procurement.domain.PurchaseRequestItem;
import java.util.UUID;

public record ItemResponse(
        UUID id,
        String description,
        String category,
        int quantity,
        long unitPriceMinor,
        String currency,
        UUID supplierId,
        long lineTotalMinor) {

    public static ItemResponse from(PurchaseRequestItem item) {
        return new ItemResponse(
                item.getId(),
                item.getDescription(),
                item.getCategory(),
                item.getQuantity(),
                item.getUnitPriceMinor(),
                item.getCurrency(),
                item.getSupplier() == null ? null : item.getSupplier().getId(),
                item.lineTotalMinor());
    }
}
