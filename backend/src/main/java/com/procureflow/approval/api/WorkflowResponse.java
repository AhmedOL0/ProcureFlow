package com.procureflow.approval.api;

import com.procureflow.approval.domain.ApprovalWorkflow;
import java.time.Instant;
import java.util.UUID;

public record WorkflowResponse(
        UUID id,
        String name,
        long minAmountMinor,
        Long maxAmountMinor,
        int escalateAfterDays,
        boolean active,
        Instant updatedAt) {

    public static WorkflowResponse from(ApprovalWorkflow workflow) {
        return new WorkflowResponse(
                workflow.getId(),
                workflow.getName(),
                workflow.getMinAmountMinor(),
                workflow.getMaxAmountMinor(),
                workflow.getEscalateAfterDays(),
                workflow.isActive(),
                workflow.getUpdatedAt());
    }
}
