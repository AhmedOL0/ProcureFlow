package com.procureflow.organization.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateDepartmentRequest(@NotBlank @Size(max = 200) String name) {
}
