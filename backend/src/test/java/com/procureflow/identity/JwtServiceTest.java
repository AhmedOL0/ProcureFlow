package com.procureflow.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.procureflow.identity.application.AuthenticatedUser;
import com.procureflow.identity.application.JwtService;
import com.procureflow.identity.infrastructure.JwtServiceImpl;
import com.procureflow.shared.web.ApiException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Pure unit tests for token issuance and validation. No Spring, no database. */
class JwtServiceTest {

    private static final String SECRET =
            "test-only-0123456789-test-only-0123456789-test-only-0123456789-12";

    private final JwtService jwt = new JwtServiceImpl(SECRET, 900_000);

    @Test
    void roundtripCarriesAllClaims() {
        AuthenticatedUser user = new AuthenticatedUser(
                UUID.randomUUID(), "acme", "ada@acme.test", Set.of("procurement:request"));

        AuthenticatedUser parsed = jwt.parseAccessToken(jwt.generateAccessToken(user));

        assertEquals(user, parsed);
    }

    @Test
    void expiredTokenIsRejected() {
        JwtService expired = new JwtServiceImpl(SECRET, -1_000);
        String token = expired.generateAccessToken(
                new AuthenticatedUser(UUID.randomUUID(), "acme", "ada@acme.test", Set.of()));

        ApiException failure =
                assertThrows(ApiException.class, () -> jwt.parseAccessToken(token));
        assertEquals("INVALID_TOKEN", failure.getCode());
    }

    @Test
    void tamperedTokenIsRejected() {
        String token = jwt.generateAccessToken(
                new AuthenticatedUser(UUID.randomUUID(), "acme", "ada@acme.test", Set.of()));
        String tampered = token.substring(0, token.length() - 2) + "ab";

        ApiException failure =
                assertThrows(ApiException.class, () -> jwt.parseAccessToken(tampered));
        assertEquals("INVALID_TOKEN", failure.getCode());
    }

    @Test
    void tokenFromAnotherKeyIsRejected() {
        JwtService other = new JwtServiceImpl(
                "completely-different-test-secret-0123456789-completely-diff-0011", 900_000);
        String token = other.generateAccessToken(
                new AuthenticatedUser(UUID.randomUUID(), "acme", "ada@acme.test", Set.of()));

        assertThrows(ApiException.class, () -> jwt.parseAccessToken(token));
    }

    @Test
    void tokenMissingClaimsIsRejected() {
        String bare = Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();

        ApiException failure =
                assertThrows(ApiException.class, () -> jwt.parseAccessToken(bare));
        assertEquals("INVALID_TOKEN", failure.getCode());
    }

    @Test
    void weakSecretRefusesToStart() {
        IllegalStateException failure =
                assertThrows(IllegalStateException.class, () -> new JwtServiceImpl("too-short", 900_000));
        assertTrue(failure.getMessage().contains("64"));
    }

    @Test
    void documentedPlaceholderRefusesToStart() {
        // The application.yml placeholder is 61 chars: a missing JWT_SECRET
        // already fails loudly on the length gate, never boots insecure.
        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> new JwtServiceImpl(
                        "change-me-min-64-chars-for-hs512-signing-key-please-rotate-me", 900_000));
        assertTrue(failure.getMessage().contains("64"));
    }

    @Test
    void garbageIsRejected() {
        assertThrows(ApiException.class, () -> jwt.parseAccessToken("not-a-token"));
    }

    @Test
    void ttlIsExposedInSeconds() {
        assertEquals(900, jwt.accessTokenTtlSeconds());
    }
}
