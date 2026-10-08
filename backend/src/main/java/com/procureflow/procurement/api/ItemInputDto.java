package com.procureflow.procurement.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record ItemInputDto(
        @NotBlank @Size(max = 500) String description,
        @Size(max = 200) String category,
        @NotNull @Min(1) Integer quantity,
        @NotNull @Min(0) Long unitPriceMinor,
        @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be a 3-letter ISO code") String currency,
        UUID supplierId) {
}
