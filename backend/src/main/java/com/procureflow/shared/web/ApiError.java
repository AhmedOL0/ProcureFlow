package com.procureflow.shared.web;

import java.time.Instant;
import java.util.Map;

/**
 * Consistent error envelope for every API failure, as documented in
 * {@code docs/api/README.md}. Never carries stack traces or secrets.
 */
public record ApiError(
        String code,
        String message,
        Map<String, String> details,
        String traceId,
        Instant timestamp) {

    public static ApiError of(String code, String message) {
        return new ApiError(code, message, Map.of(), java.util.UUID.randomUUID().toString(), Instant.now());
    }

    public static ApiError of(String code, String message, Map<String, String> details) {
        return new ApiError(code, message, details, java.util.UUID.randomUUID().toString(), Instant.now());
    }
}
