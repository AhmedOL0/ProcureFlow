package com.procureflow.approval.api;

import com.procureflow.approval.application.ApprovalService;
import com.procureflow.approval.application.DelegationService;
import com.procureflow.approval.application.WorkflowService;
import com.procureflow.approval.domain.ApprovalDecision;
import com.procureflow.identity.application.AuthenticatedUser;
import com.procureflow.identity.application.PermissionCodes;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Approval decisions, delegations, workflows and assignments. Deciding needs
 * the assigned approver or an active delegation (members without either
 * answer 403); managing workflows and delegations needs
 * {@code procurement:approve}. Cross-tenant reads/writes answer 404.
 */
@RestController
@RequestMapping("/api/v1/approvals")
@Tag(name = "approvals", description = "Decide submitted requests; delegate decide power; approval lanes")
public class ApprovalController {

    private final ApprovalService approvals;
    private final DelegationService delegations;
    private final WorkflowService workflows;

    public ApprovalController(ApprovalService approvals, DelegationService delegations, WorkflowService workflows) {
        this.approvals = approvals;
        this.delegations = delegations;
        this.workflows = workflows;
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

    @PostMapping("/workflows")
    @PreAuthorize("hasAuthority('procurement:approve')")
    @Operation(summary = "Create an approval lane matching request totals")
    public ResponseEntity<WorkflowResponse> createWorkflow(
            @AuthenticationPrincipal AuthenticatedUser principal, @Valid @RequestBody CreateWorkflowRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(WorkflowResponse.from(workflows.create(
                principal.tenantId(),
                request.name(),
                request.minAmountMinor(),
                request.maxAmountMinor(),
                request.escalateAfterDays() == null ? 3 : request.escalateAfterDays())));
    }

    @GetMapping("/workflows")
    @PreAuthorize("hasAuthority('procurement:approve')")
    @Operation(summary = "List approval lanes of the current workspace")
    public ResponseEntity<List<WorkflowResponse>> listWorkflows(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(workflows.list(principal.tenantId()).stream()
                .map(WorkflowResponse::from)
                .toList());
    }

    @PatchMapping("/workflows/{id}")
    @PreAuthorize("hasAuthority('procurement:approve')")
    @Operation(summary = "Rename, retime or activate/deactivate a lane (null fields unchanged)")
    public ResponseEntity<WorkflowResponse> updateWorkflow(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateWorkflowRequest request) {
        return ResponseEntity.ok(WorkflowResponse.from(workflows.update(
                principal.tenantId(), id, request.name(), request.escalateAfterDays(), request.active())));
    }

    @PostMapping("/workflows/{id}/steps")
    @PreAuthorize("hasAuthority('procurement:approve')")
    @Operation(summary = "Name an approver for one ordered lane level")
    public ResponseEntity<StepResponse> addStep(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID id,
            @Valid @RequestBody AddStepRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(StepResponse.from(
                workflows.addStep(principal.tenantId(), id, request.stepOrder(), request.approverId())));
    }

    @GetMapping("/workflows/{id}/steps")
    @PreAuthorize("hasAuthority('procurement:approve')")
    @Operation(summary = "List the ordered levels of one lane")
    public ResponseEntity<List<StepResponse>> listSteps(
            @AuthenticationPrincipal AuthenticatedUser principal, @PathVariable UUID id) {
        return ResponseEntity.ok(workflows.steps(principal.tenantId(), id).stream()
                .map(StepResponse::from)
                .toList());
    }

    @DeleteMapping("/workflows/{id}/steps/{stepId}")
    @PreAuthorize("hasAuthority('procurement:approve')")
    @Operation(summary = "Remove one lane level")
    public ResponseEntity<Void> removeStep(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID id,
            @PathVariable UUID stepId) {
        workflows.removeStep(principal.tenantId(), id, stepId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/assignments")
    @PreAuthorize("hasAnyAuthority('procurement:approve','procurement:request')")
    @Operation(summary = "Pin a submitted request to its matching lane (idempotent)")
    public ResponseEntity<Map<String, Object>> attach(
            @AuthenticationPrincipal AuthenticatedUser principal, @Valid @RequestBody AttachRequest body) {
        return ResponseEntity.ok(Map.of(
                "assigned", approvals.attach(principal.tenantId(), body.requestId()).isPresent()));
    }

    @GetMapping("/state")
    @PreAuthorize("hasAnyAuthority('procurement:approve','procurement:request')")
    @Operation(summary = "Current approval step for one request (404 when no lane matched)")
    public ResponseEntity<AssignmentResponse> state(
            @AuthenticationPrincipal AuthenticatedUser principal, @RequestParam UUID requestId) {
        return approvals
                .state(principal.tenantId(), requestId)
                .map(view -> ResponseEntity.ok(AssignmentResponse.from(view)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private boolean isAdmin(AuthenticatedUser principal) {
        return principal.authorities().contains(PermissionCodes.TENANT_ADMIN);
    }
}
