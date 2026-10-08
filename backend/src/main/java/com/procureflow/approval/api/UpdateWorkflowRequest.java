package com.procureflow.approval.api;

import jakarta.validation.constraints.Min;

/** Null fields mean "leave unchanged". */
public record UpdateWorkflowRequest(
        String name,
        @Min(1) Integer escalateAfterDays,
        Boolean active) {
}
