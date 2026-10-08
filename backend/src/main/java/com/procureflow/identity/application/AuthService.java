package com.procureflow.identity.application;

import com.procureflow.identity.domain.Permission;
import com.procureflow.identity.domain.RefreshToken;
import com.procureflow.identity.domain.User;
import com.procureflow.identity.infrastructure.RefreshTokenRepository;
import com.procureflow.identity.infrastructure.UserRepository;
import com.procureflow.organization.application.TenantProvisioning;
import com.procureflow.organization.domain.Tenant;
import com.procureflow.shared.web.ApiException;
import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registration, login, refresh rotation and logout. Registration doubles as
 * tenant provisioning: an unknown slug creates the tenant (caller becomes
 * admin), a known slug joins it as a member. Refresh tokens are opaque,
 * single-use and hashed at rest; presenting a spent token revokes the whole
 * chain (theft response).
 */
@Service
@Transactional
public class AuthService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final TenantProvisioning tenants;
    private final RoleProvisioningService roleProvisioning;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final EntityManager entities;

    @Value("${app.jwt.refresh-expiration-ms}")
    private long refreshExpirationMs;

    public AuthService(
            UserRepository users,
            RefreshTokenRepository refreshTokens,
            TenantProvisioning tenants,
            RoleProvisioningService roleProvisioning,
            PasswordEncoder encoder,
            JwtService jwt,
            EntityManager entities) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.tenants = tenants;
        this.roleProvisioning = roleProvisioning;
        this.encoder = encoder;
        this.jwt = jwt;
        this.entities = entities;
    }

    public AuthResult register(Registration cmd) {
        PasswordPolicy.requireValid(cmd.password());
        String email = normalize(cmd.email());
        String slug = normalize(cmd.tenantSlug());
        UUID tenantId;
        Set<String> roleNames;
        if (tenants.exists(slug)) {
            if (users.findByTenantSlugAndEmail(slug, email).isPresent()) {
                throw ApiException.conflict("EMAIL_IN_USE", "This email is already registered in this workspace");
            }
            tenantId = tenants.requireTenantId(slug);
            roleNames = Set.of(RoleProvisioningService.MEMBER);
        } else {
            if (cmd.tenantName() == null || cmd.tenantName().isBlank()) {
                throw ApiException.badRequest(
                        "TENANT_NAME_REQUIRED", "tenantName is required to create a new workspace");
            }
            tenantId = tenants.ensureTenant(slug, cmd.tenantName().trim());
            roleNames = Set.of(RoleProvisioningService.TENANT_ADMIN);
        }
        User user = new User(entities.getReference(Tenant.class, tenantId), email, encoder.encode(cmd.password()));
        user.setFirstName(cmd.firstName());
        user.setLastName(cmd.lastName());
        user.getRoles().addAll(roleProvisioning.resolveRoles(slug, roleNames));
        users.save(user);
        return new AuthResult(user, issueTokens(user));
    }

    public AuthResult login(Credentials cmd) {
        String email = normalize(cmd.email());
        List<User> candidates = users.findAllByEmail(email);
        User user;
        if (cmd.tenantSlug() != null && !cmd.tenantSlug().isBlank()) {
            String slug = normalize(cmd.tenantSlug());
            user = candidates.stream()
                    .filter(u -> u.getTenant().getSlug().equals(slug))
                    .findFirst()
                    .orElseThrow(() -> ApiException.unauthorized("INVALID_CREDENTIALS", "Invalid email or password"));
        } else if (candidates.size() == 1) {
            user = candidates.get(0);
        } else if (candidates.isEmpty()) {
            throw ApiException.unauthorized("INVALID_CREDENTIALS", "Invalid email or password");
        } else {
            throw ApiException.conflict(
                    "LOGIN_AMBIGUOUS", "This email exists in several workspaces; specify tenantSlug");
        }
        if (user.getStatus() != User.Status.ACTIVE || !encoder.matches(cmd.password(), user.getPasswordHash())) {
            throw ApiException.unauthorized("INVALID_CREDENTIALS", "Invalid email or password");
        }
        return new AuthResult(user, issueTokens(user));
    }

    public AuthResult refresh(String presentedToken) {
        RefreshToken presented = refreshTokens
                .findByTokenHash(hash(presentedToken))
                .orElseThrow(() -> ApiException.unauthorized("INVALID_REFRESH_TOKEN", "Refresh token is invalid"));
        if (presented.isRevoked()) {
            revokeAllActive(presented.getUser().getId());
            throw ApiException.unauthorized("TOKEN_REUSED", "Refresh token reuse detected; all sessions revoked");
        }
        if (presented.isExpired(Instant.now())) {
            presented.revoke(null);
            throw ApiException.unauthorized("REFRESH_EXPIRED", "Refresh token has expired; please log in again");
        }
        User user = presented.getUser();
        TokenPair pair = issueTokens(user);
        RefreshToken replacement = refreshTokens.findByTokenHash(hash(pair.refreshToken())).orElseThrow();
        presented.revoke(replacement);
        return new AuthResult(user, pair);
    }

    public void logout(String presentedToken) {
        refreshTokens.findByTokenHash(hash(presentedToken)).ifPresent(token -> token.revoke(null));
    }

    @Transactional(readOnly = true)
    public User me(UUID userId) {
        return users
                .findDetailedById(userId)
                .orElseThrow(() -> ApiException.unauthorized("INVALID_CREDENTIALS", "Invalid email or password"));
    }

    private TokenPair issueTokens(User user) {
        Set<String> authorities = user.getRoles().stream()
                .flatMap(role -> role.getPermissions().stream())
                .map(Permission::getCode)
                .collect(Collectors.toSet());
        String access = jwt.generateAccessToken(
                new AuthenticatedUser(user.getId(), user.getTenant().getSlug(), user.getEmail(), authorities));
        String refreshRaw = randomToken();
        RefreshToken stored = new RefreshToken(
                user, hash(refreshRaw), Instant.now().plusMillis(refreshExpirationMs));
        refreshTokens.save(stored);
        return new TokenPair(access, refreshRaw, jwt.accessTokenTtlSeconds());
    }

    private void revokeAllActive(UUID userId) {
        for (RefreshToken token : refreshTokens.findAllByUser_IdAndRevokedFalse(userId)) {
            token.revoke(null);
        }
    }

    private static String normalize(String value) {
        return value == null ? null : value.trim().toLowerCase();
    }

    static String randomToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
