package com.procureflow.approval.application;

import com.procureflow.approval.domain.ApprovalStep;
import com.procureflow.approval.domain.ApprovalWorkflow;
import com.procureflow.approval.infrastructure.ApprovalAssignmentRepository;
import com.procureflow.approval.infrastructure.ApprovalStepRepository;
import com.procureflow.approval.infrastructure.ApprovalWorkflowRepository;
import com.procureflow.identity.application.UserLookup;
import com.procureflow.organization.application.TenantProvisioning;
import com.procureflow.shared.web.ApiException;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Approval lanes and their ordered steps. Workflows match requests by total
 * amount (most specific lower bound wins); steps name one approver each.
 * Only active workflows with at least one step attach to new requests.
 */
@Service
public class WorkflowService {

    private final ApprovalWorkflowRepository workflows;
    private final ApprovalStepRepository steps;
    private final ApprovalAssignmentRepository assignments;
    private final TenantProvisioning tenants;
    private final UserLookup users;

    public WorkflowService(
            ApprovalWorkflowRepository workflows,
            ApprovalStepRepository steps,
            ApprovalAssignmentRepository assignments,
            TenantProvisioning tenants,
            UserLookup users) {
        this.workflows = workflows;
        this.steps = steps;
        this.assignments = assignments;
        this.tenants = tenants;
        this.users = users;
    }

    @Transactional
    public ApprovalWorkflow create(
            String tenantSlug, String name, long minAmountMinor, Long maxAmountMinor, int escalateAfterDays) {
        UUID tenantId = tenants.requireTenantId(tenantSlug);
        if (name == null || name.isBlank()) {
            throw ApiException.badRequest("NAME_REQUIRED", "Workflow name is required");
        }
        if (escalateAfterDays < 1) {
            throw ApiException.badRequest("INVALID_ESCALATION", "escalateAfterDays must be at least 1");
        }
        if (maxAmountMinor != null && maxAmountMinor <= minAmountMinor) {
            throw ApiException.badRequest("INVALID_RANGE", "maxAmountMinor must be greater than minAmountMinor");
        }
        return workflows.save(new ApprovalWorkflow(
                tenantId, name.trim(), minAmountMinor, maxAmountMinor, escalateAfterDays));
    }

    @Transactional(readOnly = true)
    public List<ApprovalWorkflow> list(String tenantSlug) {
        return workflows.findAllByTenantIdOrderByMinAmountMinorAsc(tenants.requireTenantId(tenantSlug));
    }

    @Transactional
    public ApprovalWorkflow update(
            String tenantSlug, UUID id, String name, Integer escalateAfterDays, Boolean active) {
        ApprovalWorkflow workflow = scoped(tenantSlug, id);
        if (name != null && !name.isBlank()) {
            workflow.setName(name.trim());
        }
        if (escalateAfterDays != null) {
            if (escalateAfterDays < 1) {
                throw ApiException.badRequest("INVALID_ESCALATION", "escalateAfterDays must be at least 1");
            }
            workflow.setEscalateAfterDays(escalateAfterDays);
        }
        if (active != null) {
            workflow.setActive(active);
        }
        return workflows.save(workflow);
    }

    @Transactional(readOnly = true)
    public List<ApprovalStep> steps(String tenantSlug, UUID workflowId) {
        scoped(tenantSlug, workflowId);
        return steps.findAllByWorkflowIdOrderByStepOrderAsc(workflowId);
    }

    @Transactional
    public ApprovalStep addStep(String tenantSlug, UUID workflowId, int stepOrder, UUID approverId) {
        ApprovalWorkflow workflow = scoped(tenantSlug, workflowId);
        if (stepOrder < 1) {
            throw ApiException.badRequest("INVALID_STEP", "stepOrder starts at 1");
        }
        if (!users.existsInTenant(approverId, tenantSlug)) {
            throw ApiException.notFound("USER_NOT_FOUND", "Approver not found in this workspace");
        }
        if (stepExists(workflowId, stepOrder)) {
            throw ApiException.conflict("STEP_EXISTS", "This workflow already has step " + stepOrder);
        }
        try {
            return steps.saveAndFlush(new ApprovalStep(workflow.getId(), stepOrder, approverId));
        } catch (DataIntegrityViolationException e) {
            throw ApiException.conflict("STEP_EXISTS", "This workflow already has step " + stepOrder);
        }
    }

    @Transactional
    public void removeStep(String tenantSlug, UUID workflowId, UUID stepId) {
        ApprovalWorkflow workflow = scoped(tenantSlug, workflowId);
        ApprovalStep step = steps.findById(stepId)
                .filter(s -> s.getWorkflowId().equals(workflow.getId()))
                .orElseThrow(() -> ApiException.notFound("STEP_NOT_FOUND", "Approval step not found"));
        steps.delete(step);
    }

    private boolean stepExists(UUID workflowId, int stepOrder) {
        return steps.existsByWorkflowIdAndStepOrder(workflowId, stepOrder);
    }

    private ApprovalWorkflow scoped(String tenantSlug, UUID id) {
        UUID tenantId = tenants.requireTenantId(tenantSlug);
        return workflows
                .findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> ApiException.notFound("WORKFLOW_NOT_FOUND", "Approval workflow not found"));
    }
}
