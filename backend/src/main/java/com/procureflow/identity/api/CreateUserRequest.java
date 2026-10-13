package com.procureflow.identity.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.Set;

public record CreateUserRequest(
        @Email @NotBlank String email,
        @NotBlank @Size(min = 12, max = 72, message = "Password must be 12 to 72 characters") String password,
        String firstName,
        String lastName,
        @NotEmpty Set<String> roleNames) {
}
