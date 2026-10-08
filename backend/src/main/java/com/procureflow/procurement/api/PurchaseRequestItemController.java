package com.procureflow.procurement.api;

import com.procureflow.identity.application.AuthenticatedUser;
import com.procureflow.identity.application.PermissionCodes;
import com.procureflow.procurement.application.PurchaseRequestItemService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Direct item edits on draft requests (listing/creation live nested under requests). */
@RestController
@RequestMapping("/api/v1/purchase-request-items")
@Tag(name = "purchase-request-items", description = "Direct request-item edits")
@PreAuthorize("hasAuthority('procurement:request')")
public class PurchaseRequestItemController {

    private final PurchaseRequestItemService items;

    public PurchaseRequestItemController(PurchaseRequestItemService items) {
        this.items = items;
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Update an item of a draft request")
    public ResponseEntity<ItemResponse> update(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateItemRequest request) {
        return ResponseEntity.ok(ItemResponse.from(items.update(
                principal.tenantId(),
                id,
                principal.userId(),
                principal.authorities().contains(PermissionCodes.TENANT_ADMIN),
                request.description(),
                request.category(),
                request.quantity(),
                request.unitPriceMinor(),
                request.currency(),
                request.supplierId())));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Remove an item from a draft request")
    public ResponseEntity<Void> remove(
            @AuthenticationPrincipal AuthenticatedUser principal, @PathVariable UUID id) {
        items.remove(
                principal.tenantId(),
                id,
                principal.userId(),
                principal.authorities().contains(PermissionCodes.TENANT_ADMIN));
        return ResponseEntity.noContent().build();
    }
}
