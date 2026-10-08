package com.procureflow.identity.application;

/** A freshly issued token pair. The refresh token is the only copy ever shown; only its hash is stored. */
public record TokenPair(String accessToken, String refreshToken, long expiresInSeconds) {
}
