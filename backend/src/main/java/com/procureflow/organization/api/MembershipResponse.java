package com.procureflow.organization.api;

import com.procureflow.organization.domain.Membership;
import java.util.UUID;

public record MembershipResponse(UUID id, UUID userId, UUID departmentId) {

    public static MembershipResponse from(Membership membership) {
        return new MembershipResponse(
                membership.getId(), membership.getUser().getId(), membership.getDepartment().getId());
    }
}
