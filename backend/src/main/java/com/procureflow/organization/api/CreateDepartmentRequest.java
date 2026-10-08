package com.procureflow.organization.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CreateDepartmentRequest(@NotBlank @Size(max = 200) String name, UUID parentId) {
}
