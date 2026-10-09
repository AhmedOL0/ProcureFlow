package com.procureflow.supplier.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateCategoryRequest(
        @NotBlank @Size(max = 200) String name, @Size(max = 500) String description) {
}
