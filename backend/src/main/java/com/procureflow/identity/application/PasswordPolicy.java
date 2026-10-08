package com.procureflow.identity.application;

import com.procureflow.shared.web.ApiException;

/**
 * Minimal, explainable password policy: length only. Complexity rules add
 * support load without measurable security gain at our threat level; length
 * plus BCrypt-12 plus rate-limited login is the defense. Tighten here if a
 * customer contract demands it.
 */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 12;

    private PasswordPolicy() {
        // static only
    }

    public static void requireValid(String password) {
        if (password == null || password.length() < MIN_LENGTH) {
            throw ApiException.badRequest("WEAK_PASSWORD", "Password must be at least 12 characters");
        }
    }
}
