package com.procureflow.supplier.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AddContactRequest(
        @NotBlank @Size(max = 200) String name,
        @Email String email,
        @Size(max = 50) String phone,
        @Size(max = 100) String title,
        boolean primary) {
}
