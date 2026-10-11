package com.procureflow.identity.application;

/**
 * Registration intent, mapped from the API DTO by the controller.
 * {@code inviteToken} is required when the slug names an existing
 * workspace, ignored when it creates one.
 */
public record Registration(
        String email,
        String password,
        String firstName,
        String lastName,
        String tenantSlug,
        String tenantName,
        String inviteToken) {
}
