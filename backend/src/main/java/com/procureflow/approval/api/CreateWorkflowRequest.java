package com.procureflow.approval.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Create an approval lane matching request totals in [{@code minAmountMinor}, {@code maxAmountMinor}). */
public record CreateWorkflowRequest(
        @NotBlank String name,
        @NotNull @Min(0) Long minAmountMinor,
        @Min(1) Long maxAmountMinor,
        @Min(1) Integer escalateAfterDays) {
}
