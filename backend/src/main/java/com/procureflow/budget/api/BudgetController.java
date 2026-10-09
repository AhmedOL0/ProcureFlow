package com.procureflow.budget.api;

import com.procureflow.budget.application.BudgetService;
import com.procureflow.identity.application.AuthenticatedUser;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Budget pots and their reservations. Approving a request reserves its total
 * in the same transaction as the status change; overspend answers 409.
 * Cross-tenant reads/writes answer 404.
 */
@RestController
@RequestMapping("/api/v1/budgets")
@Tag(name = "budgets", description = "Monthly spending pots and approval reservations")
public class BudgetController {

    private final BudgetService budgets;

    public BudgetController(BudgetService budgets) {
        this.budgets = budgets;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('budget:manage')")
    @Operation(summary = "Open a named pot for one month (YYYY-MM)")
    public ResponseEntity<BudgetResponse> create(
            @AuthenticationPrincipal AuthenticatedUser principal, @Valid @RequestBody CreateBudgetRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(BudgetResponse.from(budgets.view(budgets.create(
                principal.tenantId(),
                principal.userId(),
                request.name(),
                request.period(),
                request.amountMinor(),
                request.currency()))));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('budget:read')")
    @Operation(summary = "List pots of the current workspace, newest period first")
    public ResponseEntity<List<BudgetResponse>> list(
            @AuthenticationPrincipal AuthenticatedUser principal, @RequestParam(required = false) String period) {
        return ResponseEntity.ok(budgets.list(principal.tenantId(), period).stream()
                .map(budgets::view)
                .map(BudgetResponse::from)
                .toList());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('budget:read')")
    @Operation(summary = "One pot with its reserved and remaining amounts")
    public ResponseEntity<BudgetResponse> get(
            @AuthenticationPrincipal AuthenticatedUser principal, @PathVariable UUID id) {
        return ResponseEntity.ok(BudgetResponse.from(budgets.get(principal.tenantId(), id)));
    }

    @GetMapping("/{id}/reservations")
    @PreAuthorize("hasAuthority('budget:read')")
    @Operation(summary = "Holds currently resting on one pot")
    public ResponseEntity<List<ReservationResponse>> reservations(
            @AuthenticationPrincipal AuthenticatedUser principal, @PathVariable UUID id) {
        return ResponseEntity.ok(budgets.reservationsFor(principal.tenantId(), id).stream()
                .map(ReservationResponse::from)
                .toList());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('budget:manage')")
    @Operation(summary = "Close a pot (refused while holds rest on it)")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal AuthenticatedUser principal, @PathVariable UUID id) {
        budgets.delete(principal.tenantId(), principal.userId(), id);
        return ResponseEntity.noContent().build();
    }
}
