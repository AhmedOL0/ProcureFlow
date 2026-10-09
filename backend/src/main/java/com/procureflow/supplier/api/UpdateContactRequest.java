package com.procureflow.supplier.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

/** Null fields mean "leave unchanged", including {@code primary}. */
public record UpdateContactRequest(
        @Size(max = 200) String name,
        @Email String email,
        @Size(max = 50) String phone,
        @Size(max = 100) String title,
        Boolean primary) {
}
