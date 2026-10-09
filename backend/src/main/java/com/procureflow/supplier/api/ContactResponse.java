package com.procureflow.supplier.api;

import com.procureflow.supplier.domain.SupplierContact;
import java.util.UUID;

public record ContactResponse(
        UUID id,
        UUID supplierId,
        String name,
        String email,
        String phone,
        String title,
        boolean primary) {

    public static ContactResponse from(SupplierContact contact) {
        return new ContactResponse(
                contact.getId(),
                contact.getSupplier().getId(),
                contact.getName(),
                contact.getEmail(),
                contact.getPhone(),
                contact.getTitle(),
                contact.isPrimary());
    }
}
