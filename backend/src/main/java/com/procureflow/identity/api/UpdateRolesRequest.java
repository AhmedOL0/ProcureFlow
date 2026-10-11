package com.procureflow.identity.api;

import jakarta.validation.constraints.NotNull;
import java.util.Set;

public record UpdateRolesRequest(@NotNull Set<String> roleNames) {
}
