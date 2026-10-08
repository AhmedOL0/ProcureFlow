package com.procureflow.approval.api;

import com.procureflow.approval.domain.ApprovalDecision;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** Decide a submitted request. The decision is immutable once recorded. */
public record DecideRequest(
        @NotNull UUID requestId,
        @NotNull ApprovalDecision.Decision decision,
        String comment) {
}
