package com.procureflow.budget.api;

import com.procureflow.budget.domain.BudgetReservation;
import java.time.Instant;
import java.util.UUID;

public record ReservationResponse(
        UUID id,
        UUID requestId,
        long amountMinor,
        Instant createdAt) {

    public static ReservationResponse from(BudgetReservation reservation) {
        return new ReservationResponse(
                reservation.getId(),
                reservation.getRequestId(),
                reservation.getAmountMinor(),
                reservation.getCreatedAt());
    }
}
