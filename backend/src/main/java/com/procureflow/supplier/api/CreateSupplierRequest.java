package com.procureflow.supplier.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateSupplierRequest(
        @NotBlank @Size(max = 200) String name,
        @Size(max = 100) String taxId,
        @Email String email,
        @Size(max = 50) String phone,
        @Size(max = 500) String address) {
}
