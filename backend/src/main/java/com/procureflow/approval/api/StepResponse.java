package com.procureflow.approval.api;

import com.procureflow.approval.domain.ApprovalStep;
import java.util.UUID;

public record StepResponse(
        UUID id,
        int stepOrder,
        UUID approverId) {

    public static StepResponse from(ApprovalStep step) {
        return new StepResponse(step.getId(), step.getStepOrder(), step.getApproverId());
    }
}
