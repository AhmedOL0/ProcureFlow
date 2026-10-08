package com.procureflow.procurement.api;

import com.procureflow.procurement.domain.PurchaseRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CreatePurchaseRequestRequest(
        @NotBlank @Size(max = 200) String title,
        String description,
        PurchaseRequest.Priority priority,
        List<@Valid ItemInputDto> items) {
}
