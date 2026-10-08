package com.procureflow.supplier.api;

import com.procureflow.supplier.domain.Supplier;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

/** Null fields mean "leave unchanged". */
public record UpdateSupplierRequest(
        @Size(max = 200) String name,
        @Size(max = 100) String taxId,
        @Email String email,
        @Size(max = 50) String phone,
        @Size(max = 500) String address,
        Supplier.Status status) {
}
