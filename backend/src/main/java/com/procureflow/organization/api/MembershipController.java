package com.procureflow.organization.api;

import com.procureflow.identity.application.AuthenticatedUser;
import com.procureflow.organization.application.MembershipService;
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

/** Membership management inside the caller's own workspace. */
@RestController
@RequestMapping("/api/v1/memberships")
@Tag(name = "memberships", description = "User-department memberships")
public class MembershipController {

    private final MembershipService memberships;

    public MembershipController(MembershipService memberships) {
        this.memberships = memberships;
    }

    @GetMapping
    @Operation(summary = "List memberships of the current workspace")
    public ResponseEntity<List<MembershipResponse>> list(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(
                memberships.listByTenant(principal.tenantId()).stream().map(MembershipResponse::from).toList());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('department:manage')")
    @Operation(summary = "Add a user to a department")
    public ResponseEntity<MembershipResponse> add(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody AddMembershipRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(MembershipResponse.from(memberships.add(
                        principal.tenantId(), request.userId(), request.departmentId())));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('department:manage')")
    @Operation(summary = "Remove a membership")
    public ResponseEntity<Void> remove(
            @AuthenticationPrincipal AuthenticatedUser principal, @PathVariable UUID id) {
        memberships.remove(principal.tenantId(), id);
        return ResponseEntity.noContent().build();
    }
}
