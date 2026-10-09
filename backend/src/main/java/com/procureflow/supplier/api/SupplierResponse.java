package com.procureflow.supplier.api;

import com.procureflow.supplier.domain.Supplier;
import java.util.UUID;

public record SupplierResponse(
        UUID id,
        String tenantSlug,
        String name,
        String taxId,
        String email,
        String phone,
        String address,
        String status) {

    public static SupplierResponse from(Supplier supplier) {
        return new SupplierResponse(
                supplier.getId(),
                supplier.getTenant().getSlug(),
                supplier.getName(),
                supplier.getTaxId(),
                supplier.getEmail(),
                supplier.getPhone(),
                supplier.getAddress(),
                supplier.getStatus().name());
    }
}
