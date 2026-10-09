package com.procureflow.budget.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Open a named spending pot for one UTC month ({@code YYYY-MM}). */
public record CreateBudgetRequest(
        @NotBlank String name,
        @NotNull String period,
        @NotNull @Min(0) Long amountMinor,
        String currency) {
}
