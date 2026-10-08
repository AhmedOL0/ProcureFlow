package com.procureflow.identity.infrastructure;

import com.procureflow.identity.application.AuthenticatedUser;
import com.procureflow.identity.application.JwtService;
import com.procureflow.shared.web.ApiException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * HS512 JWT implementation. The secret must be at least 64 characters; the
 * bean refuses to start otherwise so a weak key can never reach production
 * silently.
 */
@Component
public class JwtServiceImpl implements JwtService {

    private final SecretKey key;
    private final long expirationMs;

    public JwtServiceImpl(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expiration-ms}") long expirationMs) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 64) {
            throw new IllegalStateException("app.jwt.secret must be at least 64 characters (HS512 needs 512 bits)");
        }
        this.key = Keys.hmacShaKeyFor(bytes);
        this.expirationMs = expirationMs;
    }

    @Override
    public String generateAccessToken(AuthenticatedUser user) {
        Date now = new Date();
        return Jwts.builder()
                .subject(user.userId().toString())
                .claim("tenant", user.tenantId())
                .claim("email", user.email())
                .claim("authorities", List.copyOf(user.authorities()))
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expirationMs))
                .signWith(key)
                .compact();
    }

    @Override
    @SuppressWarnings("unchecked")
    public AuthenticatedUser parseAccessToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            String tenant = claims.get("tenant", String.class);
            String email = claims.get("email", String.class);
            List<String> authorities = claims.get("authorities", List.class);
            if (claims.getSubject() == null || tenant == null || email == null || authorities == null) {
                throw ApiException.unauthorized("INVALID_TOKEN", "Token is missing required claims");
            }
            return new AuthenticatedUser(
                    UUID.fromString(claims.getSubject()), tenant, email, new HashSet<>(authorities));
        } catch (JwtException | IllegalArgumentException e) {
            throw ApiException.unauthorized("INVALID_TOKEN", "Invalid or expired token");
        }
    }

    @Override
    public long accessTokenTtlSeconds() {
        return expirationMs / 1000;
    }
}
