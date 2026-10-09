package com.procureflow.approval.api;

import com.procureflow.approval.domain.ApprovalDecision;
import java.time.Instant;
import java.util.UUID;

public record DecisionResponse(
        UUID id,
        UUID requestId,
        UUID deciderId,
        String decision,
        String comment,
        Instant createdAt) {

    public static DecisionResponse from(ApprovalDecision decision) {
        return new DecisionResponse(
                decision.getId(),
                decision.getRequestId(),
                decision.getDeciderId(),
                decision.getDecision().name(),
                decision.getComment(),
                decision.getCreatedAt());
    }
}
