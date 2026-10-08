package com.procureflow.approval.api;

import com.procureflow.approval.application.ApprovalService.AssignmentView;
import java.time.Instant;
import java.util.UUID;

public record AssignmentResponse(
        UUID workflowId,
        int stepOrder,
        UUID approverId,
        Instant dueAt,
        boolean escalated) {

    public static AssignmentResponse from(AssignmentView view) {
        return new AssignmentResponse(
                view.workflowId(), view.stepOrder(), view.approverId(), view.dueAt(), view.escalated());
    }
}
