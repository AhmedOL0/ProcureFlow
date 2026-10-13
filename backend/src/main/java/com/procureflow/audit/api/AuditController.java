package com.procureflow.audit.api;

import com.procureflow.audit.application.AuditService;
import com.procureflow.audit.domain.AuditEvent;
import com.procureflow.identity.application.AuthenticatedUser;
import com.procureflow.shared.document.Filenames;
import com.procureflow.shared.document.Spreadsheet;
import com.procureflow.shared.web.Paged;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
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
    @Operation(summary = "Paged trail rows, newest first, optionally filtered and searched")
    public ResponseEntity<Paged<AuditEventResponse>> list(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) UUID entityId,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return ResponseEntity.ok(trail
                .page(
                        principal.tenantId(),
                        entityType,
                        entityId,
                        q,
                        Paged.pageOrThrow(page),
                        Paged.sizeOrThrow(size))
                .map(AuditEventResponse::from));
    }

    @GetMapping("/export.xlsx")
    @PreAuthorize("hasAuthority('audit:read')")
    @Operation(summary = "Export the filtered trail as xlsx (who/what/when, no payloads, max 5000 rows)")
    public ResponseEntity<byte[]> export(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) UUID entityId,
            @RequestParam(required = false) String q) {
        List<List<Object>> body = trail.exportRows(principal.tenantId(), entityType, entityId, q).stream()
                .map(AuditController::row)
                .toList();
        byte[] content = Spreadsheet.book(
                "Audit",
                List.of("created_at", "actor", "action", "entity_type", "entity_id"),
                body);
        return ResponseEntity.ok()
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(Filenames.xlsx("audit-trail", principal.tenantId()))
                                .build()
                                .toString())
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(content);
    }

    private static List<Object> row(AuditEvent event) {
        return List.of(
                event.getCreatedAt(),
                event.getActorId() == null ? "" : event.getActorId().toString(),
                event.getAction() == null ? "" : event.getAction(),
                event.getEntityType() == null ? "" : event.getEntityType(),
                event.getEntityId() == null ? "" : event.getEntityId().toString());
    }
}
