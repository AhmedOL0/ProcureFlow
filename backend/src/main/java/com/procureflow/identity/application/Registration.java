package com.procureflow.identity.application;

/** Registration intent, mapped from the API DTO by the controller. */
public record Registration(
        String email,
        String password,
        String firstName,
        String lastName,
        String tenantSlug,
        String tenantName) {
}
