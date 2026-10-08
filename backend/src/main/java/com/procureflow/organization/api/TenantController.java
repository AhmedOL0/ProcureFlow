package com.procureflow.organization.api;

import com.procureflow.identity.application.AuthenticatedUser;
import com.procureflow.organization.application.TenantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Self-service reads and admin renames for the caller's own workspace. */
@RestController
@RequestMapping("/api/v1/tenants/current")
@Tag(name = "tenants", description = "Current workspace")
public class TenantController {

    private final TenantService tenants;

    public TenantController(TenantService tenants) {
        this.tenants = tenants;
    }

    @GetMapping
    @Operation(summary = "Get the current workspace")
    public ResponseEntity<TenantResponse> current(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(TenantResponse.from(tenants.current(principal.tenantId())));
    }

    @PatchMapping
    @PreAuthorize("hasAuthority('tenant:manage')")
    @Operation(summary = "Rename the current workspace")
    public ResponseEntity<TenantResponse> rename(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody UpdateTenantRequest request) {
        return ResponseEntity.ok(TenantResponse.from(tenants.rename(principal.tenantId(), request.name())));
    }
}
