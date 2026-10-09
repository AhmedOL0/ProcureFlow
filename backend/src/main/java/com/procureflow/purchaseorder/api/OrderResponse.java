package com.procureflow.purchaseorder.api;

import com.procureflow.purchaseorder.domain.OrderItem;
import com.procureflow.purchaseorder.domain.PurchaseOrder;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderResponse(
        UUID id,
        UUID requestId,
        UUID supplierId,
        String status,
        String currency,
        long totalMinor,
        List<OrderLineResponse> lines,
        Instant createdAt) {

    public static OrderResponse from(PurchaseOrder order, List<OrderItem> lines) {
        return new OrderResponse(
                order.getId(),
                order.getRequestId(),
                order.getSupplierId(),
                order.getStatus().name(),
                order.getCurrency(),
                lines.stream().mapToLong(OrderItem::lineTotalMinor).sum(),
                lines.stream().map(OrderLineResponse::from).toList(),
                order.getCreatedAt());
    }
}
