package com.procureflow.approval.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** Name one approver for an ordered workflow level. */
public record AddStepRequest(
        @Min(1) int stepOrder,
        @NotNull UUID approverId) {
}
