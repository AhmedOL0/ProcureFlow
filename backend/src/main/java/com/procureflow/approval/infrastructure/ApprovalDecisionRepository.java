package com.procureflow.approval.infrastructure;

import com.procureflow.approval.domain.ApprovalDecision;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence adapter for immutable approval decisions. Used only by the approval module. */
public interface ApprovalDecisionRepository extends JpaRepository<ApprovalDecision, UUID> {

    Optional<ApprovalDecision> findByTenantIdAndRequestId(UUID tenantId, UUID requestId);

    boolean existsByRequestId(UUID requestId);

    List<ApprovalDecision> findAllByTenantIdOrderByCreatedAtDesc(UUID tenantId);
}
