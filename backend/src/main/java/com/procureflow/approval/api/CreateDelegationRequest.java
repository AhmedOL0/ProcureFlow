package com.procureflow.approval.api;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

/** Grant decide power to a workspace member until {@code endsAt}. */
public record CreateDelegationRequest(
        @NotNull UUID delegateId,
        @NotNull Instant endsAt) {
}
