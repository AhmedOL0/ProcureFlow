package com.procureflow.organization.api;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record AddMembershipRequest(@NotNull UUID userId, @NotNull UUID departmentId) {
}
