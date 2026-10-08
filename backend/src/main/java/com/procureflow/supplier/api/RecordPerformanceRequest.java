package com.procureflow.supplier.api;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;

public record RecordPerformanceRequest(
        @NotBlank @Pattern(regexp = "^\\d{4}-(0[1-9]|1[0-2])$", message = "Period must be YYYY-MM") String period,
        @DecimalMin("0") @DecimalMax("100") BigDecimal onTimeRate,
        @DecimalMin("0") @DecimalMax("100") BigDecimal qualityScore,
        String notes) {
}
