package com.procureflow.approval.application;

import com.procureflow.approval.domain.ApprovalAssignment;
import com.procureflow.approval.domain.ApprovalDecision;
import com.procureflow.approval.domain.ApprovalStep;
import com.procureflow.approval.domain.ApprovalWorkflow;
import com.procureflow.approval.infrastructure.ApprovalAssignmentRepository;
import com.procureflow.approval.infrastructure.ApprovalDecisionRepository;
import com.procureflow.approval.infrastructure.ApprovalDelegationRepository;
import com.procureflow.approval.infrastructure.ApprovalStepRepository;
import com.procureflow.approval.infrastructure.ApprovalWorkflowRepository;
import com.procureflow.audit.application.AuditTrailLogged;
import com.procureflow.budget.application.BudgetReservationPort;
import io.micrometer.core.instrument.MeterRegistry;
import com.procureflow.organization.application.TenantProvisioning;
import com.procureflow.procurement.application.RequestDecisionPort;
import com.procureflow.shared.web.ApiException;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records immutable approve/reject verdicts on submitted purchase requests.
 * The request row is locked (FOR UPDATE) before the verdict is stored, and the
 * status flip commits in the same transaction — concurrent deciders serialize
 * and exactly one wins.
 *
 * <p>When a workflow matches the request total, the request is pinned to its
 * current step and only that step's approver (or an active delegate) may
 * decide; overdue steps escalate to the next level first. Without a matching
 * workflow the slice-1 rule holds: any approver or active delegate may decide.
 */
@Service
public class ApprovalService {

    private final ApprovalDecisionRepository decisions;
    private final ApprovalDelegationRepository delegations;
    private final ApprovalWorkflowRepository workflows;
    private final ApprovalStepRepository steps;
    private final ApprovalAssignmentRepository assignments;
    private final RequestDecisionPort requests;
    private final BudgetReservationPort budgets;
    private final ApplicationEventPublisher events;
    private final MeterRegistry meters;
    private final TenantProvisioning tenants;

    public ApprovalService(
            ApprovalDecisionRepository decisions,
            ApprovalDelegationRepository delegations,
            ApprovalWorkflowRepository workflows,
            ApprovalStepRepository steps,
            ApprovalAssignmentRepository assignments,
            RequestDecisionPort requests,
            BudgetReservationPort budgets,
            ApplicationEventPublisher events,
            MeterRegistry meters,
            TenantProvisioning tenants) {
        this.decisions = decisions;
        this.delegations = delegations;
        this.workflows = workflows;
        this.steps = steps;
        this.assignments = assignments;
        this.requests = requests;
        this.budgets = budgets;
        this.events = events;
        this.meters = meters;
        this.tenants = tenants;
    }

    @Transactional
    public ApprovalDecision decide(
            String tenantSlug, UUID requestId, UUID deciderId, boolean approver, boolean approved, String comment) {
        UUID tenantId = tenants.requireTenantId(tenantSlug);
        RequestDecisionPort.LockedRequest locked = requests.lock(tenantSlug, requestId);
        if (!"SUBMITTED".equals(locked.status())) {
            throw ApiException.conflict("INVALID_TRANSITION", "Only submitted requests can be decided");
        }
        if (decisions.existsByRequestId(requestId)) {
            throw ApiException.conflict("ALREADY_DECIDED", "This request already has a decision");
        }
        ApprovalAssignment assignment = attached(tenantId, tenantSlug, locked);
        UUID requiredApprover = currentApprover(assignment);
        if (requiredApprover != null) {
            if (!requiredApprover.equals(deciderId) && !isActiveDelegate(tenantId, deciderId)) {
                throw ApiException.forbidden("NOT_ASSIGNED", "Only the assigned approver or a delegate can decide");
            }
        } else if (!approver && !isActiveDelegate(tenantId, deciderId)) {
            throw ApiException.forbidden("NOT_APPROVER", "Only an approver or an active delegate can decide");
        }
        try {
            ApprovalDecision decision = new ApprovalDecision(
                    tenantId,
                    requestId,
                    deciderId,
                    approved ? ApprovalDecision.Decision.APPROVED : ApprovalDecision.Decision.REJECTED,
                    comment);
            decisions.saveAndFlush(decision);
            requests.applyDecision(tenantSlug, requestId, approved);
            if (approved) {
                budgets.reserve(tenantSlug, requestId, locked.totalMinor(), locked.currency());
            }
            events.publishEvent(PurchaseRequestDecided.now(
                    tenantSlug, requestId, locked.requesterId(), deciderId, approved, comment));
            Map<String, Object> after = new HashMap<>();
            after.put("status", approved ? "APPROVED" : "REJECTED");
            after.put("deciderId", deciderId.toString());
            if (comment != null) {
                after.put("comment", comment);
            }
            events.publishEvent(AuditTrailLogged.now(
                    tenantSlug, deciderId, "REQUEST_DECIDED", "purchase_request", requestId,
                    Map.of("status", "SUBMITTED"), after));
            meters.counter("procureflow.decisions", "verdict", approved ? "approved" : "rejected").increment();
            return decision;
        } catch (DataIntegrityViolationException e) {
            throw ApiException.conflict("ALREADY_DECIDED", "This request already has a decision");
        }
    }

    @Transactional(readOnly = true)
    public ApprovalDecision getForRequest(String tenantSlug, UUID requestId) {
        UUID tenantId = tenants.requireTenantId(tenantSlug);
        return decisions
                .findByTenantIdAndRequestId(tenantId, requestId)
                .orElseThrow(() -> ApiException.notFound("DECISION_NOT_FOUND", "No decision recorded for this request"));
    }

    /** Pins a submitted request to its matching workflow, or returns the existing pin. */
    @Transactional
    public Optional<ApprovalAssignment> attach(String tenantSlug, UUID requestId) {
        UUID tenantId = tenants.requireTenantId(tenantSlug);
        RequestDecisionPort.LockedRequest locked = requests.lock(tenantSlug, requestId);
        if (!"SUBMITTED".equals(locked.status())) {
            throw ApiException.conflict("INVALID_TRANSITION", "Only submitted requests enter approval");
        }
        return Optional.ofNullable(attached(tenantId, tenantSlug, locked));
    }

    /** Current step view for one request; empty when no workflow matched. */
    @Transactional
    public Optional<AssignmentView> state(String tenantSlug, UUID requestId) {
        UUID tenantId = tenants.requireTenantId(tenantSlug);
        // A read lock keeps the tenant check first (404 across tenants) without writing.
        requests.lock(tenantSlug, requestId);
        return assignments
                .findByTenantIdAndRequestId(tenantId, requestId)
                .flatMap(assignment -> currentStep(assignment).map(step -> new AssignmentView(
                        assignment.getWorkflowId(),
                        step.getStepOrder(),
                        step.getApproverId(),
                        assignment.getDueAt(),
                        assignment.isEscalated())));
    }

    /** Advances every overdue assignment one level; the hourly scheduler and tests drive this. */
    @Transactional
    public int escalateOverdue(Instant now) {
        int advanced = 0;
        for (ApprovalAssignment assignment : assignments.findAllByDueAtBefore(now)) {
            if (advanceIfOverdue(assignment, now)) {
                advanced++;
            }
        }
        return advanced;
    }

    private ApprovalAssignment attached(
            UUID tenantId, String tenantSlug, RequestDecisionPort.LockedRequest locked) {
        Optional<ApprovalAssignment> existing =
                assignments.findByTenantIdAndRequestId(tenantId, locked.requestId());
        if (existing.isPresent()) {
            advanceIfOverdue(existing.get(), Instant.now());
            return existing.get();
        }
        Optional<ApprovalWorkflow> match = workflows.findAllByTenantIdOrderByMinAmountMinorAsc(tenantId).stream()
                .filter(ApprovalWorkflow::isActive)
                .filter(w -> w.matches(locked.totalMinor()))
                .filter(w -> !steps.findAllByWorkflowIdOrderByStepOrderAsc(w.getId()).isEmpty())
                .reduce((first, second) -> second);
        if (match.isEmpty()) {
            return null;
        }
        ApprovalWorkflow workflow = match.get();
        ApprovalStep first = steps.findAllByWorkflowIdOrderByStepOrderAsc(workflow.getId()).get(0);
        try {
            return assignments.saveAndFlush(new ApprovalAssignment(
                    tenantId,
                    locked.requestId(),
                    workflow.getId(),
                    first.getStepOrder(),
                    Instant.now().plusSeconds(workflow.getEscalateAfterDays() * 86400L)));
        } catch (DataIntegrityViolationException e) {
            return assignments
                    .findByTenantIdAndRequestId(tenantId, locked.requestId())
                    .orElseThrow(() -> ApiException.conflict("ALREADY_ASSIGNED", "This request is already in approval"));
        }
    }

    private boolean advanceIfOverdue(ApprovalAssignment assignment, Instant now) {
        if (!assignment.isOverdue(now)) {
            return false;
        }
        // Tenant-scoped lookup: the scheduler sweeps all tenants, so a bare
        // findById could escalate one tenant's assignment under another
        // tenant's lane. A foreign workflow answers empty and skips.
        // Tenant-scoped lookup: the scheduler sweeps all tenants, so a bare
        // findById could escalate one tenant's assignment under another
        // tenant's lane. A foreign workflow answers empty and skips.
        Optional<ApprovalWorkflow> workflow =
                workflows.findByIdAndTenantId(assignment.getWorkflowId(), assignment.getTenantId());
        if (workflow.isEmpty()) {
            return false;
        }
        List<ApprovalStep> ordered =
                steps.findAllByWorkflowIdOrderByStepOrderAsc(assignment.getWorkflowId());
        Optional<ApprovalStep> next = ordered.stream()
                .filter(s -> s.getStepOrder() > assignment.getCurrentStepOrder())
                .findFirst();
        if (next.isEmpty()) {
            return false;
        }
        assignment.escalateTo(
                next.get().getStepOrder(), now.plusSeconds(workflow.get().getEscalateAfterDays() * 86400L));
        assignments.save(assignment);
        return true;
    }

    private UUID currentApprover(ApprovalAssignment assignment) {
        if (assignment == null) {
            return null;
        }
        return currentStep(assignment).map(ApprovalStep::getApproverId).orElse(null);
    }

    private Optional<ApprovalStep> currentStep(ApprovalAssignment assignment) {
        return steps.findByWorkflowIdAndStepOrder(assignment.getWorkflowId(), assignment.getCurrentStepOrder());
    }

    private boolean isActiveDelegate(UUID tenantId, UUID deciderId) {
        return !delegations.findActiveForDelegate(tenantId, deciderId, Instant.now()).isEmpty();
    }

    public record AssignmentView(UUID workflowId, int stepOrder, UUID approverId, Instant dueAt, boolean escalated) {
    }
}
