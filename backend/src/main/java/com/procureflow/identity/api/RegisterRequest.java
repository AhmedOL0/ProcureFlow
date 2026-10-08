package com.procureflow.identity.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Registration payload. {@code tenantSlug} is always required: an unknown
 * slug creates the workspace (then {@code tenantName} is required and the
 * caller becomes admin), a known slug joins it as a member.
 */
public record RegisterRequest(
        @Email @NotBlank String email,
        @NotBlank @Size(min = 12, message = "Password must be at least 12 characters") String password,
        String firstName,
        String lastName,
        @NotBlank String tenantSlug,
        String tenantName) {
}
