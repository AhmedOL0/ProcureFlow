package com.procureflow.identity.application;

/**
 * Issues and validates short-lived access tokens (HS512 JWT). Refresh tokens
 * are opaque and handled by {@code AuthService}, never here.
 */
public interface JwtService {

    String generateAccessToken(AuthenticatedUser user);

    /**
     * Parses and fully validates a token (signature, expiry, required
     * claims). Throws {@code ApiException} 401 on any problem.
     */
    AuthenticatedUser parseAccessToken(String token);

    long accessTokenTtlSeconds();
}
