package com.procureflow.approval.api;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** Pin a submitted request to its matching approval lane. */
public record AttachRequest(
        @NotNull UUID requestId) {
}
