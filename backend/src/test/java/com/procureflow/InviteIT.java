package com.procureflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.procureflow.identity.api.AuthResponse;
import com.procureflow.identity.application.MailPort;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;

import static org.mockito.Mockito.verify;

/**
 * Invite-only workspace joining: invites are created/listed/revoked by
 * admins, the raw token travels only inside the mailed link (never in API
 * responses), redemption assigns the invite's roles, verifies the mailbox,
 * and admits exactly one winner. Cross-tenant use answers 404/403.
 */
class InviteIT extends AbstractIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = postgresContainer();

    @MockitoBean
    private MailPort mail;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void inviteCreateListRevokeRoundTrip() {
        String slug = uniqueSlug("inv");
        AuthResponse admin = register(slug, "boss@" + slug + ".test");

        ResponseEntity<Map> created =
                inviteRaw(admin.accessToken(), "min@" + slug + ".test", List.of("MEMBER"));
        assertEquals(HttpStatus.CREATED, created.getStatusCode());
        assertNotNull(created.getBody());
        assertEquals("min@" + slug + ".test", created.getBody().get("email"));
        assertTrue(!created.getBody().toString().contains("invite="));

        ResponseEntity<List> listed = get("/api/v1/invites", admin.accessToken(), List.class);
        assertEquals(HttpStatus.OK, listed.getStatusCode());
        assertEquals(1, listed.getBody().size());

        @SuppressWarnings("unchecked")
        String id = ((Map<String, Object>) listed.getBody().get(0)).get("id").toString();
        assertEquals(
                HttpStatus.NO_CONTENT,
                rest.exchange(
                                "/api/v1/invites/" + id,
                                HttpMethod.DELETE,
                                new HttpEntity<>(bearer(admin.accessToken())),
                                Void.class)
                        .getStatusCode());

        ResponseEntity<List> empty = get("/api/v1/invites", admin.accessToken(), List.class);
        assertEquals(HttpStatus.OK, empty.getStatusCode());
        assertTrue(empty.getBody().isEmpty());
    }

    @Test
    void inviteJoinAssignsRolesAndVerifies() {
        String slug = uniqueSlug("inv");
        AuthResponse admin = register(slug, "boss@" + slug + ".test");
        String email = "fin@" + slug + ".test";
        String token = inviteToken(admin.accessToken(), email, List.of("FINANCE"));

        ResponseEntity<AuthResponse> joined = rest.postForEntity(
                "/api/v1/auth/register",
                Map.of(
                        "email", email,
                        "password", "correct-horse-123",
                        "tenantSlug", slug,
                        "inviteToken", token),
                AuthResponse.class);
        assertEquals(HttpStatus.CREATED, joined.getStatusCode());
        assertNotNull(joined.getBody());
        assertTrue(joined.getBody().user().roles().contains("FINANCE"));
        assertTrue(joined.getBody().user().verified());
    }

    @Test
    void inviteBoundToAddressTenantAndSingleUse() {
        String slug = uniqueSlug("inv");
        AuthResponse admin = register(slug, "boss@" + slug + ".test");
        String token = inviteToken(admin.accessToken(), "min@" + slug + ".test", List.of("MEMBER"));

        assertEquals(
                HttpStatus.FORBIDDEN,
                rest.postForEntity(
                                "/api/v1/auth/register",
                                Map.of(
                                        "email", "other@" + slug + ".test",
                                        "password", "correct-horse-123",
                                        "tenantSlug", slug,
                                        "inviteToken", token),
                                String.class)
                        .getStatusCode());

        String otherSlug = uniqueSlug("inv");
        register(otherSlug, "boss@" + otherSlug + ".test");
        assertEquals(
                HttpStatus.FORBIDDEN,
                rest.postForEntity(
                                "/api/v1/auth/register",
                                Map.of(
                                        "email", "min@" + slug + ".test",
                                        "password", "correct-horse-123",
                                        "tenantSlug", otherSlug,
                                        "inviteToken", token),
                                String.class)
                        .getStatusCode());

        assertEquals(
                HttpStatus.CREATED,
                rest.postForEntity(
                                "/api/v1/auth/register",
                                Map.of(
                                        "email", "min@" + slug + ".test",
                                        "password", "correct-horse-123",
                                        "tenantSlug", slug,
                                        "inviteToken", token),
                                AuthResponse.class)
                        .getStatusCode());
        assertEquals(
                HttpStatus.FORBIDDEN,
                rest.postForEntity(
                                "/api/v1/auth/register",
                                Map.of(
                                        "email", "min2@" + slug + ".test",
                                        "password", "correct-horse-123",
                                        "tenantSlug", slug,
                                        "inviteToken", token),
                                String.class)
                        .getStatusCode());
    }

    @Test
    void expiredInviteRefused() {
        String slug = uniqueSlug("inv");
        AuthResponse admin = register(slug, "boss@" + slug + ".test");
        String email = "old@" + slug + ".test";
        String token = inviteToken(admin.accessToken(), email, List.of("MEMBER"));

        assertEquals(
                1,
                jdbc.update(
                        "UPDATE invites SET expires_at = now() - interval '1 hour' WHERE email = ?", email));

        assertEquals(
                HttpStatus.FORBIDDEN,
                rest.postForEntity(
                                "/api/v1/auth/register",
                                Map.of(
                                        "email", email,
                                        "password", "correct-horse-123",
                                        "tenantSlug", slug,
                                        "inviteToken", token),
                                String.class)
                        .getStatusCode());
    }

    @Test
    void inviteValidationAndMemberForbidden() {
        String slug = uniqueSlug("inv");
        AuthResponse admin = register(slug, "boss@" + slug + ".test");
        AuthResponse member = joinAsMember(admin.accessToken(), slug, "min@" + slug + ".test");

        assertEquals(
                HttpStatus.BAD_REQUEST,
                inviteRaw(admin.accessToken(), "min2@" + slug + ".test", List.of("NOPE")).getStatusCode());
        assertEquals(
                HttpStatus.BAD_REQUEST,
                inviteRaw(admin.accessToken(), "min2@" + slug + ".test", List.of()).getStatusCode());
        assertEquals(
                HttpStatus.BAD_REQUEST,
                inviteRaw(admin.accessToken(), "not-an-email", List.of("MEMBER")).getStatusCode());
        assertEquals(
                HttpStatus.FORBIDDEN,
                inviteRaw(member.accessToken(), "min2@" + slug + ".test", List.of("MEMBER")).getStatusCode());
    }

    @Test
    void invitesAreInvisibleAcrossTenants() {
        String slugA = uniqueSlug("inva");
        AuthResponse adminA = register(slugA, "boss@" + slugA + ".test");
        String slugB = uniqueSlug("invb");
        AuthResponse adminB = register(slugB, "boss@" + slugB + ".test");
        inviteToken(adminA.accessToken(), "min@" + slugA + ".test", List.of("MEMBER"));

        ResponseEntity<List> foreign = get("/api/v1/invites", adminB.accessToken(), List.class);
        assertEquals(HttpStatus.OK, foreign.getStatusCode());
        assertNotNull(foreign.getBody());
        assertTrue(foreign.getBody().isEmpty());
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

    private AuthResponse joinAsMember(String adminToken, String slug, String email) {
        ResponseEntity<AuthResponse> response = rest.postForEntity(
                "/api/v1/auth/register",
                Map.of(
                        "email", email,
                        "password", "correct-horse-123",
                        "tenantSlug", slug,
                        "inviteToken", inviteToken(adminToken, email, List.of("MEMBER"))),
                AuthResponse.class);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        return response.getBody();
    }

    private String inviteToken(String adminToken, String email, List<String> roles) {
        ResponseEntity<Map> invited = inviteRaw(adminToken, email, roles);
        assertEquals(HttpStatus.CREATED, invited.getStatusCode());
        ArgumentCaptor<MailPort.OutgoingMail> sent = ArgumentCaptor.forClass(MailPort.OutgoingMail.class);
        verify(mail, org.mockito.Mockito.atLeastOnce()).send(sent.capture());
        return sent.getAllValues().stream()
                .filter(message -> message.to().equals(email))
                .reduce((first, second) -> second)
                .map(message -> {
                    Matcher matcher = Pattern.compile("invite=([A-Za-z0-9_-]+)").matcher(message.textBody());
                    assertTrue(matcher.find());
                    return matcher.group(1);
                })
                .orElseThrow();
    }

    private ResponseEntity<Map> inviteRaw(String adminToken, String email, List<String> roles) {
        HttpHeaders headers = bearer(adminToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return rest.exchange(
                "/api/v1/invites",
                HttpMethod.POST,
                new HttpEntity<>(Map.of("email", email, "roleNames", roles), headers),
                Map.class);
    }
}
