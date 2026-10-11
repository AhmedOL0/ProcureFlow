package com.procureflow.identity.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.Set;

public record CreateInviteRequest(
        @Email @NotBlank String email,
        @NotEmpty Set<String> roleNames) {
}
