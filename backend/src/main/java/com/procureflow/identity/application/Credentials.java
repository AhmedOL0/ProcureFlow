package com.procureflow.identity.application;

/** Login intent. {@code tenantSlug} is optional unless the email exists in several tenants. */
public record Credentials(String email, String password, String tenantSlug) {
}
