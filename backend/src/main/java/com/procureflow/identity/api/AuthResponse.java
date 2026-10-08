package com.procureflow.identity.api;

public record AuthResponse(
        String tokenType,
        String accessToken,
        String refreshToken,
        long expiresInSeconds,
        UserResponse user) {

    public static AuthResponse bearer(
            String accessToken, String refreshToken, long expiresInSeconds, UserResponse user) {
        return new AuthResponse("Bearer", accessToken, refreshToken, expiresInSeconds, user);
    }
}
