package com.procureflow.procurement.api;

import com.procureflow.identity.application.AuthenticatedUser;
import com.procureflow.identity.application.PermissionCodes;
import com.procureflow.procurement.application.PurchaseRequestService;
import com.procureflow.procurement.application.PurchaseRequestService.Creation;
import com.procureflow.procurement.application.PurchaseRequestService.ItemInput;
import com.procureflow.procurement.domain.PurchaseRequest;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Purchase requests. Creation requires an {@code Idempotency-Key} header:
 * replays answer 200 with the original request (plus an
 * {@code X-Replay: true} header) instead of duplicating it. Mutations need
 * {@code procurement:request}; drafts additionally require ownership or
 * tenant admin.
 */
@RestController
@RequestMapping("/api/v1/purchase-requests")
@Tag(name = "purchase-requests", description = "Purchase request lifecycle")
public class PurchaseRequestController {

    private final PurchaseRequestService requests;

    public PurchaseRequestController(PurchaseRequestService requests) {
        this.requests = requests;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('procurement:request')")
    @Operation(summary = "Create a draft (idempotent per Idempotency-Key)")
    public ResponseEntity<PurchaseRequestResponse> create(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody CreatePurchaseRequestRequest request) {
        Creation creation = requests.creation(
                principal.tenantId(),
                principal.userId(),
                key,
                request.title(),
                request.description(),
                request.priority(),
                toInputs(request.items()));
        PurchaseRequestResponse body = toResponse(creation.request());
        if (creation.replayed()) {
            return ResponseEntity.ok().header("X-Replay", "true").body(body);
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @GetMapping
    @Operation(summary = "List requests of the current workspace, newest first")
    public ResponseEntity<List<PurchaseRequestResponse>> list(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(required = false) PurchaseRequest.Status status) {
        return ResponseEntity.ok(requests.list(principal.tenantId(), status).stream()
                .map(this::toResponse)
                .toList());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get one request with its items and total")
    public ResponseEntity<PurchaseRequestResponse> get(
            @AuthenticationPrincipal AuthenticatedUser principal, @PathVariable UUID id) {
        return ResponseEntity.ok(toResponse(requests.get(principal.tenantId(), id)));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('procurement:request')")
    @Operation(summary = "Edit a draft request")
    public ResponseEntity<PurchaseRequestResponse> update(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID id,
            @Valid @RequestBody UpdatePurchaseRequestRequest request) {
        return ResponseEntity.ok(toResponse(requests.update(
                principal.tenantId(), id, principal.userId(), isAdmin(principal),
                request.title(), request.description(), request.priority())));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('procurement:request')")
    @Operation(summary = "Delete a draft request with its items")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal AuthenticatedUser principal, @PathVariable UUID id) {
        requests.delete(principal.tenantId(), id, principal.userId(), isAdmin(principal));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/items")
    @PreAuthorize("hasAuthority('procurement:request')")
    @Operation(summary = "Add an item to a draft request")
    public ResponseEntity<ItemResponse> addItem(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID id,
            @Valid @RequestBody ItemInputDto input) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ItemResponse.from(requests.addItem(
                        principal.tenantId(), id, principal.userId(), isAdmin(principal), toInput(input))));
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAuthority('procurement:request')")
    @Operation(summary = "Submit a draft for approval (needs at least one item)")
    public ResponseEntity<PurchaseRequestResponse> submit(
            @AuthenticationPrincipal AuthenticatedUser principal, @PathVariable UUID id) {
        return ResponseEntity.ok(toResponse(
                requests.submit(principal.tenantId(), id, principal.userId(), isAdmin(principal))));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('procurement:request')")
    @Operation(summary = "Cancel a draft or submitted request")
    public ResponseEntity<PurchaseRequestResponse> cancel(
            @AuthenticationPrincipal AuthenticatedUser principal, @PathVariable UUID id) {
        return ResponseEntity.ok(toResponse(
                requests.cancel(principal.tenantId(), id, principal.userId(), isAdmin(principal))));
    }

    private PurchaseRequestResponse toResponse(PurchaseRequest request) {
        // Items ride the entity graph, so no extra queries happen here.
        List<ItemResponse> items = request.getItems() == null
                ? List.of()
                : request.getItems().stream().map(ItemResponse::from).toList();
        return PurchaseRequestResponse.from(request, items);
    }

    private List<ItemInput> toInputs(List<ItemInputDto> dtos) {
        if (dtos == null) {
            return List.of();
        }
        return dtos.stream().map(this::toInput).toList();
    }

    private ItemInput toInput(ItemInputDto dto) {
        return new ItemInput(
                dto.description(), dto.category(), dto.quantity(),
                dto.unitPriceMinor(), dto.currency(), dto.supplierId());
    }

    private boolean isAdmin(AuthenticatedUser principal) {
        return principal.authorities().contains(PermissionCodes.TENANT_ADMIN);
    }
}
