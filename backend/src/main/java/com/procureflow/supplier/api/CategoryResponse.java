package com.procureflow.supplier.api;

import com.procureflow.supplier.domain.SupplierCategory;
import java.util.UUID;

public record CategoryResponse(UUID id, String name, String description) {

    public static CategoryResponse from(SupplierCategory category) {
        return new CategoryResponse(category.getId(), category.getName(), category.getDescription());
    }
}
