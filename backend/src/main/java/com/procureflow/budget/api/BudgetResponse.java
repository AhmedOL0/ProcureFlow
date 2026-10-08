package com.procureflow.budget.api;

import com.procureflow.budget.application.BudgetService.BudgetView;
import java.util.UUID;

public record BudgetResponse(
        UUID id,
        String name,
        String period,
        long amountMinor,
        String currency,
        long reservedMinor,
        long remainingMinor) {

    public static BudgetResponse from(BudgetView view) {
        return new BudgetResponse(
                view.id(),
                view.name(),
                view.period(),
                view.amountMinor(),
                view.currency(),
                view.reservedMinor(),
                view.remainingMinor());
    }
}
