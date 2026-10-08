package com.procureflow.procurement.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** Null fields mean "leave unchanged". */
public record UpdateItemRequest(
        @Size(max = 500) String description,
        @Size(max = 200) String category,
        @Min(1) Integer quantity,
        @Min(0) Long unitPriceMinor,
        @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be a 3-letter ISO code") String currency,
        UUID supplierId) {
}
