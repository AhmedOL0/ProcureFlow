package com.procureflow.procurement.api;

import com.procureflow.procurement.domain.PurchaseRequest;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PurchaseRequestResponse(
        UUID id,
        String tenantSlug,
        String title,
        String description,
        String priority,
        String status,
        UUID requesterId,
        List<ItemResponse> items,
        long totalMinor,
        Instant submittedAt,
        Instant createdAt) {

    public static PurchaseRequestResponse from(PurchaseRequest request, List<ItemResponse> items) {
        long total = items.stream().mapToLong(ItemResponse::lineTotalMinor).sum();
        return new PurchaseRequestResponse(
                request.getId(),
                request.getTenant().getSlug(),
                request.getTitle(),
                request.getDescription(),
                request.getPriority().name(),
                request.getStatus().name(),
                request.getRequester().getId(),
                items,
                total,
                request.getSubmittedAt(),
                request.getCreatedAt());
    }
}
