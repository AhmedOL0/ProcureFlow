package com.procureflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.procureflow.identity.api.AuthResponse;
import com.procureflow.identity.api.UserResponse;
import com.procureflow.identity.application.MailPort;
import com.procureflow.identity.domain.PasswordResetToken;
import com.procureflow.identity.domain.User;
import com.procureflow.identity.infrastructure.PasswordResetTokenRepository;
import com.procureflow.identity.infrastructure.UserRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.MailSendException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;

/**
 * Account management: profile edits, self-service and helpdesk password
 * changes, and the email-link reset flow. Enumeration resistance is
 * asserted, not assumed: unknown accounts get the same answers as known
 * ones, and raw tokens never touch the database.
 */
class AccountIT extends AbstractIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = postgresContainer();

    @MockitoBean
    private MailPort mail;

    @Autowired
    private PasswordResetTokenRepository resetTokens;

    @Autowired
    private UserRepository userRepository;

    @Test
    void forgotPasswordIsGenericForUnknownAccounts() {
        String slug = uniqueSlug("acct");
        register(slug, "boss@" + slug + ".test");

        ResponseEntity<String> response = post(
                "/api/v1/auth/forgot-password",
                null,
                Map.of("email", "nobody@" + slug + ".test", "tenantSlug", slug),
                String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().contains("reset link"));
        verify(mail, never()).send(any());
    }

    @Test
    void forgotPasswordMintsHashedTokenAndMailsLink() {
        String slug = uniqueSlug("acct");
        String email = "boss@" + slug + ".test";
        register(slug, email);

        ResponseEntity<String> response = post(
                "/api/v1/auth/forgot-password", null, Map.of("email", email), String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        ArgumentCaptor<MailPort.OutgoingMail> sent = ArgumentCaptor.forClass(MailPort.OutgoingMail.class);
        verify(mail, times(1)).send(sent.capture());
        assertEquals(email, sent.getValue().to());
        String raw = tokenFrom(sent.getValue().textBody());
        assertTrue(resetTokens.findByTokenHash(raw).isEmpty(), "raw token must never be stored");
        assertTrue(resetTokens.findByTokenHash(sha256(raw)).isPresent(), "only the hash is stored");
    }

    @Test
    void forgotPasswordStaysGenericWhenMailSubmissionFails() {
        String slug = uniqueSlug("acct");
        String email = "boss@" + slug + ".test";
        register(slug, email);
        doThrow(new MailSendException("relay down")).when(mail).send(any());

        ResponseEntity<String> response = post(
                "/api/v1/auth/forgot-password", null, Map.of("email", email), String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().contains("reset link"));
    }

    @Test
    void forgotPasswordThrottlesPerEmail() {
        String slug = uniqueSlug("acct");
        String email = "boss@" + slug + ".test";
        register(slug, email);

        List<HttpStatusCode> outcomes = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            outcomes.add(post("/api/v1/auth/forgot-password", null, Map.of("email", email), String.class)
                    .getStatusCode());
        }
        assertEquals(List.of(
                HttpStatus.OK, HttpStatus.OK, HttpStatus.OK, HttpStatus.OK, HttpStatus.OK,
                HttpStatus.TOO_MANY_REQUESTS), outcomes);
    }

    @Test
    void resetPasswordConsumesTokenOnce() {
        String raw = mintToken(uniqueSlug("acct"));

        assertEquals(HttpStatus.NO_CONTENT, reset(raw, "correct-horse-123").getStatusCode());
        ResponseEntity<String> replay =
                reset(raw, "another-horse-123");
        assertEquals(HttpStatus.GONE, replay.getStatusCode());
        assertTrue(replay.getBody().contains("TOKEN_SPENT"));
    }

    @Test
    void resetPasswordRejectsUnknownTokens() {
        ResponseEntity<String> response = reset("definitely-not-a-token", "correct-horse-123");
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertTrue(response.getBody().contains("INVALID_TOKEN"));
    }

    @Test
    void resetPasswordRejectsExpiredTokens() {
        String slug = uniqueSlug("acct");
        register(slug, "boss@" + slug + ".test");
        User user = userRepository.findByTenantSlugAndEmail(slug, "boss@" + slug + ".test").orElseThrow();
        String raw = randomRaw();
        resetTokens.save(new PasswordResetToken(user, sha256(raw), Instant.now().minusSeconds(3600)));

        ResponseEntity<String> response = reset(raw, "correct-horse-123");
        assertEquals(HttpStatus.GONE, response.getStatusCode());
    }

    @Test
    void resetPasswordEnforcesPolicyWithoutBurningToken() {
        String raw = mintToken(uniqueSlug("acct"));

        ResponseEntity<String> weak = reset(raw, "short");
        assertEquals(HttpStatus.BAD_REQUEST, weak.getStatusCode());
        assertTrue(weak.getBody().contains("WEAK_PASSWORD"));

        assertEquals(HttpStatus.NO_CONTENT, reset(raw, "correct-horse-123").getStatusCode());
    }

    @Test
    void concurrentRedemptionAdmitsExactlyOneWinner() throws Exception {
        String raw = mintToken(uniqueSlug("acct"));
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Callable<HttpStatusCode>> attempts = List.of(
                    () -> reset(raw, "correct-horse-123").getStatusCode(),
                    () -> reset(raw, "another-horse-123").getStatusCode());
            List<HttpStatusCode> outcomes = new ArrayList<>();
            for (Future<HttpStatusCode> race : pool.invokeAll(attempts)) {
                outcomes.add(race.get());
            }
            assertEquals(1, outcomes.stream().filter(status -> status == HttpStatus.NO_CONTENT).count());
            assertEquals(1, outcomes.stream().filter(status -> status == HttpStatus.GONE).count());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void changePasswordRotatesCredentialsAndKillsSessions() {
        String slug = uniqueSlug("acct");
        String email = "boss@" + slug + ".test";
        AuthResponse auth = register(slug, email);

        ResponseEntity<String> changed = post(
                "/api/v1/auth/change-password",
                auth.accessToken(),
                Map.of("currentPassword", "correct-horse-123", "newPassword", "brand-new-horse-123"),
                String.class);
        assertEquals(HttpStatus.NO_CONTENT, changed.getStatusCode());

        assertEquals(HttpStatus.UNAUTHORIZED, login(email, "correct-horse-123", slug).getStatusCode());
        assertEquals(HttpStatus.OK, login(email, "brand-new-horse-123", slug).getStatusCode());
        ResponseEntity<String> staleRefresh = rest.postForEntity(
                "/api/v1/auth/refresh", Map.of("refreshToken", auth.refreshToken()), String.class);
        assertEquals(HttpStatus.UNAUTHORIZED, staleRefresh.getStatusCode());
    }

    @Test
    void changePasswordRejectsWrongCurrentPassword() {
        String slug = uniqueSlug("acct");
        String email = "boss@" + slug + ".test";
        AuthResponse auth = register(slug, email);

        ResponseEntity<String> response = post(
                "/api/v1/auth/change-password",
                auth.accessToken(),
                Map.of("currentPassword", "not-the-password", "newPassword", "brand-new-horse-123"),
                String.class);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals(HttpStatus.OK, login(email, "correct-horse-123", slug).getStatusCode());
    }

    @Test
    void adminResetLetsTargetLogInWithNewPassword() {
        String slug = uniqueSlug("acct");
        AuthResponse admin = register(slug, "boss@" + slug + ".test");
        UserResponse member = createUser(admin.accessToken(), slug, "min", "MEMBER");

        ResponseEntity<String> response = post(
                "/api/v1/users/" + member.id() + "/password",
                admin.accessToken(),
                Map.of("password", "reset-horse-123"),
                String.class);
        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        assertEquals(HttpStatus.OK, login("min@" + slug + ".test", "reset-horse-123", slug).getStatusCode());
    }

    @Test
    void profileEditsAllowlistNamesOnly() {
        String slug = uniqueSlug("acct");
        AuthResponse auth = register(slug, "boss@" + slug + ".test");

        ResponseEntity<UserResponse> updated = patch(
                "/api/v1/users/me",
                auth.accessToken(),
                Map.of(
                        "firstName", "Ada",
                        "lastName", "Lovelace",
                        "roles", List.of("MEMBER"),
                        "email", "evil@example.com",
                        "status", "INACTIVE"),
                UserResponse.class);
        assertEquals(HttpStatus.OK, updated.getStatusCode());
        assertNotNull(updated.getBody());
        assertEquals("Ada", updated.getBody().firstName());
        assertEquals("Lovelace", updated.getBody().lastName());
        assertEquals("boss@" + slug + ".test", updated.getBody().email());
        assertTrue(updated.getBody().roles().contains("TENANT_ADMIN"));
    }

    @Test
    void crossTenantProfileWritesAnswerNotFound() {
        String slugA = uniqueSlug("accta");
        AuthResponse adminA = register(slugA, "boss@" + slugA + ".test");
        String slugB = uniqueSlug("acctb");
        AuthResponse adminB = register(slugB, "boss@" + slugB + ".test");

        ResponseEntity<String> response = patch(
                "/api/v1/users/" + adminA.user().id(),
                adminB.accessToken(),
                Map.of("firstName", "Mallory"),
                String.class);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void memberCannotTouchAdminUserEndpoints() {
        String slug = uniqueSlug("acct");
        AuthResponse admin = register(slug, "boss@" + slug + ".test");
        UserResponse member = createUser(admin.accessToken(), slug, "min", "MEMBER");
        ResponseEntity<AuthResponse> memberLogin =
                login("min@" + slug + ".test", "correct-horse-123", slug);
        assertEquals(HttpStatus.OK, memberLogin.getStatusCode());
        assertNotNull(memberLogin.getBody());
        String token = memberLogin.getBody().accessToken();

        assertEquals(HttpStatus.FORBIDDEN, patch(
                "/api/v1/users/" + member.id(), token, Map.of("firstName", "X"), String.class).getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, post(
                "/api/v1/users/" + member.id() + "/password",
                token, Map.of("password", "reset-horse-123"), String.class).getStatusCode());
    }

    @Test
    void anonymousMeIsUnauthorized() {
        assertEquals(HttpStatus.UNAUTHORIZED, rest.getForEntity("/api/v1/auth/me", String.class).getStatusCode());
    }

    private String mintToken(String slug) {
        String email = "boss@" + slug + ".test";
        register(slug, email);
        post("/api/v1/auth/forgot-password", null, Map.of("email", email), String.class);
        ArgumentCaptor<MailPort.OutgoingMail> sent = ArgumentCaptor.forClass(MailPort.OutgoingMail.class);
        verify(mail, times(1)).send(sent.capture());
        return tokenFrom(sent.getValue().textBody());
    }

    private ResponseEntity<String> reset(String rawToken, String newPassword) {
        return post("/api/v1/auth/reset-password", null,
                Map.of("token", rawToken, "newPassword", newPassword), String.class);
    }

    private AuthResponse register(String tenantSlug, String email) {
        ResponseEntity<AuthResponse> response = rest.postForEntity(
                "/api/v1/auth/register",
                Map.of(
                        "email", email,
                        "password", "correct-horse-123",
                        "tenantSlug", tenantSlug,
                        "tenantName", "Workspace " + tenantSlug),
                AuthResponse.class);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        return response.getBody();
    }

    private UserResponse createUser(String adminToken, String slug, String who, String role) {
        ResponseEntity<UserResponse> response = post(
                "/api/v1/users",
                adminToken,
                Map.of("email", who + "@" + slug + ".test", "password", "correct-horse-123",
                        "roleNames", List.of(role)),
                UserResponse.class);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        return response.getBody();
    }

    private ResponseEntity<AuthResponse> login(String email, String password, String tenantSlug) {
        Map<String, String> body = tenantSlug == null
                ? Map.of("email", email, "password", password)
                : Map.of("email", email, "password", password, "tenantSlug", tenantSlug);
        return rest.postForEntity("/api/v1/auth/login", body, AuthResponse.class);
    }

    private <T> ResponseEntity<T> post(String url, String token, Object body, Class<T> type) {
        HttpHeaders headers = new HttpHeaders();
        if (token != null) {
            headers.setBearerAuth(token);
        }
        headers.setContentType(MediaType.APPLICATION_JSON);
        return rest.exchange(url, HttpMethod.POST, new HttpEntity<>(body, headers), type);
    }

    private <T> ResponseEntity<T> patch(String url, String token, Object body, Class<T> type) {
        HttpHeaders headers = new HttpHeaders();
        if (token != null) {
            headers.setBearerAuth(token);
        }
        headers.setContentType(MediaType.APPLICATION_JSON);
        return rest.exchange(url, HttpMethod.PATCH, new HttpEntity<>(body, headers), type);
    }

    private static String tokenFrom(String body) {
        Matcher matcher = Pattern.compile("token=([A-Za-z0-9_-]+)").matcher(body);
        assertTrue(matcher.find(), "mailed body must carry the reset token");
        return matcher.group(1);
    }

    private static String randomRaw() {
        return UUID.randomUUID() + "" + UUID.randomUUID();
    }

    private static String sha256(String value) {
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
