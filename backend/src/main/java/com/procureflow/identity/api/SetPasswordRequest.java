package com.procureflow.identity.api;

import jakarta.validation.constraints.NotBlank;

public record SetPasswordRequest(@NotBlank String password) {
}
