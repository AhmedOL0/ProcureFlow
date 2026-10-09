package com.procureflow.purchaseorder.api;

import com.procureflow.purchaseorder.domain.OrderItem;
import java.util.UUID;

public record OrderLineResponse(
        UUID id,
        String description,
        String category,
        int quantity,
        long unitPriceMinor,
        String currency,
        int receivedQty,
        long lineTotalMinor) {

    public static OrderLineResponse from(OrderItem line) {
        return new OrderLineResponse(
                line.getId(),
                line.getDescription(),
                line.getCategory(),
                line.getQuantity(),
                line.getUnitPriceMinor(),
                line.getCurrency(),
                line.getReceivedQty(),
                line.lineTotalMinor());
    }
}
