package com.procureflow.approval.infrastructure;

import com.procureflow.approval.domain.ApprovalWorkflow;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence adapter for approval workflows. Used only by the approval module. */
public interface ApprovalWorkflowRepository extends JpaRepository<ApprovalWorkflow, UUID> {

    Optional<ApprovalWorkflow> findByIdAndTenantId(UUID id, UUID tenantId);

    List<ApprovalWorkflow> findAllByTenantIdOrderByMinAmountMinorAsc(UUID tenantId);
}
