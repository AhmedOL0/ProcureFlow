package com.procureflow.identity.api;

import com.procureflow.identity.application.AuthResult;
import com.procureflow.identity.application.AuthService;
import com.procureflow.identity.application.AuthenticatedUser;
import com.procureflow.identity.application.Credentials;
import com.procureflow.identity.application.EmailVerificationService;
import com.procureflow.identity.application.PasswordResetService;
import com.procureflow.identity.application.Registration;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public authentication endpoints plus the current-user read. Registration
 * doubles as tenant provisioning (see {@link RegisterRequest}).
 */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "auth", description = "Registration, login, token refresh, logout")
public class AuthController {

    private final AuthService auth;
    private final PasswordResetService passwordResets;
    private final EmailVerificationService verification;

    public AuthController(
            AuthService auth, PasswordResetService passwordResets, EmailVerificationService verification) {
        this.auth = auth;
        this.passwordResets = passwordResets;
        this.verification = verification;
    }

    @PostMapping("/register")
    @Operation(summary = "Register (creates a workspace, or joins one with a live invite)")
    public ResponseEntity<AuthResponse> register(
            @Valid @RequestBody RegisterRequest request, HttpServletRequest http) {
        AuthResult result = auth.register(new Registration(
                request.email(),
                request.password(),
                request.firstName(),
                request.lastName(),
                request.tenantSlug(),
                request.tenantName(),
                request.inviteToken()),
                clientIp(http));
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(result));
    }

    @PostMapping("/login")
    @Operation(summary = "Log in with email and password")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        AuthResult result = auth.login(
                new Credentials(request.email(), request.password(), request.tenantSlug()), clientIp(http));
        return ResponseEntity.ok(toResponse(result));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Rotate a refresh token into a new pair")
    public ResponseEntity<AuthResponse> refresh(
            @Valid @RequestBody RefreshRequest request, HttpServletRequest http) {
        return ResponseEntity.ok(toResponse(auth.refresh(request.refreshToken(), clientIp(http))));
    }

    @PostMapping("/logout")
    @Operation(summary = "Revoke a refresh token (idempotent)")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshRequest request) {
        auth.logout(request.refreshToken());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/change-password")
    @Operation(summary = "Change the caller's own password, revoking all its sessions")
    public ResponseEntity<Void> changePassword(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody ChangePasswordRequest request) {
        if (principal == null) {
            // /auth/** is public so that register/login stay reachable; this
            // endpoint still requires a caller, translated here, not in XML.
            throw com.procureflow.shared.web.ApiException.unauthorized("UNAUTHENTICATED", "Authentication required");
        }
        auth.changePassword(principal.userId(), principal.tenantId(), request.currentPassword(), request.newPassword());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    @Operation(summary = "Current authenticated user")
    public ResponseEntity<UserResponse> me(@AuthenticationPrincipal AuthenticatedUser principal) {
        if (principal == null) {
            // /auth/** is public so that register/login stay reachable; this
            // endpoint still requires a caller, translated here, not in XML.
            throw com.procureflow.shared.web.ApiException.unauthorized("UNAUTHENTICATED", "Authentication required");
        }
        return ResponseEntity.ok(UserResponse.from(auth.me(principal.userId())));
    }

    @PostMapping("/forgot-password")
    @Operation(summary = "Start an email-link password reset (always the same generic answer)")
    public ResponseEntity<MessageResponse> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        return ResponseEntity.ok(
                new MessageResponse(passwordResets.requestReset(request.email(), request.tenantSlug())));
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Redeem a reset token for a new password")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        passwordResets.resetPassword(request.token(), request.newPassword());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/verify-email")
    @Operation(summary = "Redeem a mailbox-verification link")
    public ResponseEntity<Void> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        verification.verify(request.token());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/resend-verification")
    @Operation(summary = "Re-send the mailbox-verification link (always the same generic answer)")
    public ResponseEntity<MessageResponse> resendVerification(@Valid @RequestBody ResendVerificationRequest request) {
        return ResponseEntity.ok(new MessageResponse(verification.resend(request.email())));
    }

    /**
     * TCP peer address for throttle buckets. Deliberately not
     * X-Forwarded-For: that header is client-controlled without a trusted
     * proxy, and trusting it would let callers rotate their own bucket.
     * Deployments behind a proxy must front this with RemoteIpValve.
     */
    private static String clientIp(HttpServletRequest http) {
        String remote = http == null ? null : http.getRemoteAddr();
        return remote == null || remote.isBlank() ? "unknown" : remote;
    }

    private AuthResponse toResponse(AuthResult result) {
        return AuthResponse.bearer(
                result.tokens().accessToken(),
                result.tokens().refreshToken(),
                result.tokens().expiresInSeconds(),
                UserResponse.from(result.user()));
    }
}
