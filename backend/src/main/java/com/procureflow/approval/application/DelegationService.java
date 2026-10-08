package com.procureflow.approval.application;

import com.procureflow.approval.domain.ApprovalDelegation;
import com.procureflow.approval.infrastructure.ApprovalDelegationRepository;
import com.procureflow.identity.application.UserLookup;
import com.procureflow.organization.application.TenantProvisioning;
import com.procureflow.shared.web.ApiException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bounded decide-power grants. Only approvers create delegations; the delegate
 * must belong to the same tenant (foreign users answer 404, never 403).
 * Delegations are revocable by their delegator or a workspace admin.
 */
@Service
public class DelegationService {

    private final ApprovalDelegationRepository delegations;
    private final TenantProvisioning tenants;
    private final UserLookup users;

    public DelegationService(
            ApprovalDelegationRepository delegations, TenantProvisioning tenants, UserLookup users) {
        this.delegations = delegations;
        this.tenants = tenants;
        this.users = users;
    }

    @Transactional
    public ApprovalDelegation create(String tenantSlug, UUID delegatorId, UUID delegateId, Instant endsAt) {
        UUID tenantId = tenants.requireTenantId(tenantSlug);
        if (delegateId.equals(delegatorId)) {
            throw ApiException.badRequest("SELF_DELEGATION", "You cannot delegate to yourself");
        }
        if (endsAt == null || !endsAt.isAfter(Instant.now())) {
            throw ApiException.badRequest("INVALID_PERIOD", "endsAt must be in the future");
        }
        if (!users.existsInTenant(delegateId, tenantSlug)) {
            throw ApiException.notFound("USER_NOT_FOUND", "Delegate not found in this workspace");
        }
        return delegations.save(new ApprovalDelegation(tenantId, delegatorId, delegateId, endsAt));
    }

    @Transactional(readOnly = true)
    public List<ApprovalDelegation> list(String tenantSlug) {
        return delegations.findAllByTenantIdOrderByCreatedAtDesc(tenants.requireTenantId(tenantSlug));
    }

    @Transactional
    public void revoke(String tenantSlug, UUID id, UUID callerId, boolean admin) {
        UUID tenantId = tenants.requireTenantId(tenantSlug);
        ApprovalDelegation delegation = delegations
                .findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> ApiException.notFound("DELEGATION_NOT_FOUND", "Delegation not found"));
        if (!admin && !delegation.getDelegatorId().equals(callerId)) {
            throw ApiException.forbidden("NOT_DELEGATOR", "Only the delegator or a workspace admin can revoke");
        }
        delegations.delete(delegation);
    }
}
