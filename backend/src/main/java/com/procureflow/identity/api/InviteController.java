package com.procureflow.identity.api;

import com.procureflow.identity.application.AuthenticatedUser;
import com.procureflow.identity.application.InviteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Workspace invites: the only path into an existing tenant. Unknown ids
 * answer 404 so tenants cannot probe each other.
 */
@RestController
@RequestMapping("/api/v1/invites")
@Tag(name = "invites", description = "Invite-only workspace joining")
@PreAuthorize("hasAuthority('user:manage')")
public class InviteController {

    private final InviteService invites;

    public InviteController(InviteService invites) {
        this.invites = invites;
    }

    @PostMapping
    @Operation(summary = "Invite an address with a fixed role set (link mailed, token never returned)")
    public ResponseEntity<InviteResponse> invite(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody CreateInviteRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(InviteResponse.from(invites.invite(
                principal.tenantId(), principal.userId(), request.email(), request.roleNames())));
    }

    @GetMapping
    @Operation(summary = "List the workspace's invites, newest first")
    public ResponseEntity<List<InviteResponse>> list(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(invites.list(principal.tenantId()).stream()
                .map(InviteResponse::from)
                .toList());
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Revoke an invite")
    public ResponseEntity<Void> revoke(
            @AuthenticationPrincipal AuthenticatedUser principal, @PathVariable UUID id) {
        invites.revoke(principal.tenantId(), id);
        return ResponseEntity.noContent().build();
    }
}
