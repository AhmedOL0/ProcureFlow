package com.procureflow.notification.api;

import com.procureflow.identity.application.AuthenticatedUser;
import com.procureflow.identity.application.PermissionCodes;
import com.procureflow.notification.application.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Personal inbox fed by domain events. Any authenticated workspace member
 * reads their own rows; cross-tenant access answers 404.
 */
@RestController
@RequestMapping("/api/v1/notifications")
@Tag(name = "notifications", description = "Event-driven inbox for requesters")
public class NotificationController {

    private final NotificationService inbox;

    public NotificationController(NotificationService inbox) {
        this.inbox = inbox;
    }

    @GetMapping
    @Operation(summary = "My inbox, newest first")
    public ResponseEntity<List<NotificationResponse>> inbox(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(inbox.inbox(principal.tenantId(), principal.userId()).stream()
                .map(NotificationResponse::from)
                .toList());
    }

    @PatchMapping("/{id}/read")
    @Operation(summary = "Mark one row read (recipient or workspace admin)")
    public ResponseEntity<NotificationResponse> markRead(
            @AuthenticationPrincipal AuthenticatedUser principal, @PathVariable UUID id) {
        return ResponseEntity.ok(NotificationResponse.from(
                inbox.markRead(principal.tenantId(), id, principal.userId(), isAdmin(principal))));
    }

    private boolean isAdmin(AuthenticatedUser principal) {
        return principal.authorities().contains(PermissionCodes.TENANT_ADMIN);
    }
}
