package com.procureflow.procurement.api;

import com.procureflow.procurement.domain.PurchaseRequest;
import jakarta.validation.constraints.Size;

/** Null fields mean "leave unchanged". */
public record UpdatePurchaseRequestRequest(
        @Size(max = 200) String title, String description, PurchaseRequest.Priority priority) {
}
