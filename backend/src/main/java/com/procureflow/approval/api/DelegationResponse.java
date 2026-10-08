package com.procureflow.approval.api;

import com.procureflow.approval.domain.ApprovalDelegation;
import java.time.Instant;
import java.util.UUID;

public record DelegationResponse(
        UUID id,
        UUID delegatorId,
        UUID delegateId,
        Instant startsAt,
        Instant endsAt) {

    public static DelegationResponse from(ApprovalDelegation delegation) {
        return new DelegationResponse(
                delegation.getId(),
                delegation.getDelegatorId(),
                delegation.getDelegateId(),
                delegation.getStartsAt(),
                delegation.getEndsAt());
    }
}
