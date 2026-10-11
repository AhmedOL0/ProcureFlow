package com.procureflow.identity.api;

import com.procureflow.identity.domain.User;
import jakarta.validation.constraints.NotNull;

public record UpdateStatusRequest(@NotNull User.Status status) {
}
