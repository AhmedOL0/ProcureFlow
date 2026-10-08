package com.procureflow.approval.application;

import com.procureflow.approval.domain.ApprovalDecision;
import com.procureflow.approval.infrastructure.ApprovalDecisionRepository;
import com.procureflow.approval.infrastructure.ApprovalDelegationRepository;
import com.procureflow.organization.application.TenantProvisioning;
import com.procureflow.procurement.application.RequestDecisionPort;
import com.procureflow.shared.web.ApiException;
import java.time.Instant;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records immutable approve/reject verdicts on submitted purchase requests.
 * The request row is locked (FOR UPDATE) before the verdict is stored, and the
 * status flip commits in the same transaction — concurrent deciders serialize
 * and exactly one wins. Only holders of {@code procurement:approve} (checked
 * at the API boundary and passed in) or active delegates may decide.
 */
@Service
public class ApprovalService {

    private final ApprovalDecisionRepository decisions;
    private final ApprovalDelegationRepository delegations;
    private final RequestDecisionPort requests;
    private final TenantProvisioning tenants;

    public ApprovalService(
            ApprovalDecisionRepository decisions,
            ApprovalDelegationRepository delegations,
            RequestDecisionPort requests,
            TenantProvisioning tenants) {
        this.decisions = decisions;
        this.delegations = delegations;
        this.requests = requests;
        this.tenants = tenants;
    }

    @Transactional
    public ApprovalDecision decide(
            String tenantSlug, UUID requestId, UUID deciderId, boolean approver, boolean approved, String comment) {
        UUID tenantId = tenants.requireTenantId(tenantSlug);
        if (!approver && delegations.findActiveForDelegate(tenantId, deciderId, Instant.now()).isEmpty()) {
            throw ApiException.forbidden("NOT_APPROVER", "Only an approver or an active delegate can decide");
        }
        RequestDecisionPort.LockedRequest locked = requests.lock(tenantSlug, requestId);
        if (!"SUBMITTED".equals(locked.status())) {
            throw ApiException.conflict("INVALID_TRANSITION", "Only submitted requests can be decided");
        }
        if (decisions.existsByRequestId(requestId)) {
            throw ApiException.conflict("ALREADY_DECIDED", "This request already has a decision");
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
}
