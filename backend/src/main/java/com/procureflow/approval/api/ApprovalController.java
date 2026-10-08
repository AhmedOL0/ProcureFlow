package com.procureflow.approval.api;

import com.procureflow.approval.application.ApprovalService;
import com.procureflow.approval.application.DelegationService;
import com.procureflow.approval.domain.ApprovalDecision;
import com.procureflow.identity.application.AuthenticatedUser;
import com.procureflow.identity.application.PermissionCodes;
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
 * Approval decisions and delegations. Deciding needs an approver or an active
 * delegation (members without either answer 403); managing delegations needs
 * {@code procurement:approve}. Cross-tenant reads/writes answer 404.
 */
@RestController
@RequestMapping("/api/v1/approvals")
@Tag(name = "approvals", description = "Decide submitted requests; delegate decide power")
public class ApprovalController {

    private final ApprovalService approvals;
    private final DelegationService delegations;

    public ApprovalController(ApprovalService approvals, DelegationService delegations) {
        this.approvals = approvals;
        this.delegations = delegations;
    }

    @PostMapping("/decisions")
    @PreAuthorize("hasAnyAuthority('procurement:approve','procurement:request')")
    @Operation(summary = "Approve or reject a submitted request (immutable once recorded)")
    public ResponseEntity<DecisionResponse> decide(
            @AuthenticationPrincipal AuthenticatedUser principal, @Valid @RequestBody DecideRequest request) {
        boolean approver = principal.authorities().contains(PermissionCodes.PROCUREMENT_APPROVE);
        ApprovalDecision decision = approvals.decide(
                principal.tenantId(),
                request.requestId(),
                principal.userId(),
                approver,
                request.decision() == ApprovalDecision.Decision.APPROVED,
                request.comment());
        return ResponseEntity.status(HttpStatus.CREATED).body(DecisionResponse.from(decision));
    }

    @GetMapping("/decisions")
    @PreAuthorize("hasAnyAuthority('procurement:approve','procurement:request')")
    @Operation(summary = "Get the decision recorded for one request")
    public ResponseEntity<DecisionResponse> getForRequest(
            @AuthenticationPrincipal AuthenticatedUser principal, @RequestParam UUID requestId) {
        return ResponseEntity.ok(DecisionResponse.from(approvals.getForRequest(principal.tenantId(), requestId)));
    }

    @PostMapping("/delegations")
    @PreAuthorize("hasAuthority('procurement:approve')")
    @Operation(summary = "Delegate decide power to a workspace member until endsAt")
    public ResponseEntity<DelegationResponse> delegate(
            @AuthenticationPrincipal AuthenticatedUser principal, @Valid @RequestBody CreateDelegationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(DelegationResponse.from(delegations.create(
                        principal.tenantId(), principal.userId(), request.delegateId(), request.endsAt())));
    }

    @GetMapping("/delegations")
    @PreAuthorize("hasAuthority('procurement:approve')")
    @Operation(summary = "List delegations of the current workspace")
    public ResponseEntity<List<DelegationResponse>> list(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(delegations.list(principal.tenantId()).stream()
                .map(DelegationResponse::from)
                .toList());
    }

    @DeleteMapping("/delegations/{id}")
    @PreAuthorize("hasAuthority('procurement:approve')")
    @Operation(summary = "Revoke a delegation (delegator or workspace admin)")
    public ResponseEntity<Void> revoke(
            @AuthenticationPrincipal AuthenticatedUser principal, @PathVariable UUID id) {
        delegations.revoke(principal.tenantId(), id, principal.userId(), isAdmin(principal));
        return ResponseEntity.noContent().build();
    }

    private boolean isAdmin(AuthenticatedUser principal) {
        return principal.authorities().contains(PermissionCodes.TENANT_ADMIN);
    }
}
