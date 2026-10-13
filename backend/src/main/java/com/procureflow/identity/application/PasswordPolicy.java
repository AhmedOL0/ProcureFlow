package com.procureflow.identity.application;

import com.procureflow.shared.web.ApiException;
import java.nio.charset.StandardCharsets;

/**
 * Minimal, explainable password policy: length only. Complexity rules add
 * support load without measurable security gain at our threat level; length
 * plus BCrypt-12 plus rate-limited login is the defense. Tighten here if a
 * customer contract demands it.
 *
 * <p>The ceiling is a BCrypt technical limit, not a policy choice: BCrypt
 * silently truncates past 72 bytes, so accepting longer passwords would sell
 * entropy that is never hashed. It applies to creation paths only (register,
 * admin create/reset, self reset/change) — login stays uncapped so accounts
 * created before the ceiling keep verifying against their truncated hash.
 */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 12;

    public static final int MAX_BYTES = 72;

    private PasswordPolicy() {
        // static only
    }

    public static void requireValid(String password) {
        if (password == null || password.length() < MIN_LENGTH) {
            throw ApiException.badRequest("WEAK_PASSWORD", "Password must be at least 12 characters");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            throw ApiException.badRequest(
                    "PASSWORD_TOO_LONG", "Password must be at most 72 bytes (BCrypt truncates beyond that)");
        }
    }
}
