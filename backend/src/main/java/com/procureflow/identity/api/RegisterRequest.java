package com.procureflow.identity.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Registration payload. {@code tenantSlug} is always required: an unknown
 * slug creates the workspace (then {@code tenantName} is required and the
 * caller becomes admin), a known slug joins it — but only with a live
 * invite token for the registrant's address.
 */
public record RegisterRequest(
        @Email @NotBlank String email,
        @NotBlank @Size(min = 12, max = 72, message = "Password must be 12 to 72 characters") String password,
        String firstName,
        String lastName,
        @NotBlank String tenantSlug,
        String tenantName,
        String inviteToken) {
}
