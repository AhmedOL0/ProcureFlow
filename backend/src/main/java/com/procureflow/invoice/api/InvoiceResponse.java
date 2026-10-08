package com.procureflow.invoice.api;

import com.procureflow.invoice.application.InvoiceService.MatchLine;
import com.procureflow.invoice.application.InvoiceService.MatchView;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record InvoiceResponse(
        UUID id,
        UUID orderId,
        String number,
        String status,
        long totalMinor,
        String currency,
        long paidMinor,
        List<MatchLineResponse> match,
        Instant createdAt) {

    public static InvoiceResponse from(MatchView view) {
        return new InvoiceResponse(
                view.invoice().getId(),
                view.invoice().getOrderId(),
                view.invoice().getNumber(),
                view.invoice().getStatus().name(),
                view.invoice().getTotalMinor(),
                view.invoice().getCurrency(),
                view.paidMinor(),
                view.lines().stream().map(InvoiceResponse::line).toList(),
                view.invoice().getCreatedAt());
    }

    private static MatchLineResponse line(MatchLine match) {
        return new MatchLineResponse(
                match.orderItemId(), match.orderedQty(), match.receivedQty(), match.invoicedQty());
    }

    public record MatchLineResponse(UUID orderItemId, int orderedQty, int receivedQty, long invoicedQty) {
    }
}
