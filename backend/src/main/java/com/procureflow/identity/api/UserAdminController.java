package com.procureflow.identity.api;

import com.procureflow.identity.application.AuthenticatedUser;
import com.procureflow.identity.application.UserAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tenant-scoped user administration. Every read is scoped to the caller's
 * workspace; unknown ids answer 404 so tenants cannot probe each other.
 */
@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "users", description = "Tenant-scoped user administration")
@PreAuthorize("hasAuthority('user:manage')")
public class UserAdminController {

    private final UserAdminService users;

    public UserAdminController(UserAdminService users) {
        this.users = users;
    }

    @GetMapping
    @Operation(summary = "List users of the current workspace")
    public ResponseEntity<List<UserResponse>> list(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(
                users.list(principal.tenantId()).stream().map(UserResponse::from).toList());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get one user of the current workspace")
    public ResponseEntity<UserResponse> get(
            @AuthenticationPrincipal AuthenticatedUser principal, @PathVariable UUID id) {
        return ResponseEntity.ok(UserResponse.from(users.get(principal.tenantId(), id)));
    }

    @PostMapping
    @Operation(summary = "Create a user in the current workspace")
    public ResponseEntity<UserResponse> create(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody CreateUserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(UserResponse.from(users.create(
                        principal.tenantId(),
                        request.email(),
                        request.password(),
                        request.firstName(),
                        request.lastName(),
                        request.roleNames())));
    }
}
