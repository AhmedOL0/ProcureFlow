package com.procureflow.audit.api;

import com.procureflow.audit.application.AuditService;
import com.procureflow.identity.application.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only audit trail for workspace admins. The trail is append-only:
 * nothing here mutates or removes rows.
 */
@RestController
@RequestMapping("/api/v1/audit-events")
@Tag(name = "audit", description = "Append-only trail of sensitive actions")
public class AuditController {

    private final AuditService trail;

    public AuditController(AuditService trail) {
        this.trail = trail;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('audit:read')")
    @Operation(summary = "List trail rows, newest first, optionally filtered")
    public ResponseEntity<List<AuditEventResponse>> list(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) UUID entityId) {
        return ResponseEntity.ok(trail.list(principal.tenantId(), entityType, entityId).stream()
                .map(AuditEventResponse::from)
                .toList());
    }
}
