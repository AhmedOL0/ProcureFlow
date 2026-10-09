package com.procureflow.approval.infrastructure;

import com.procureflow.approval.domain.ApprovalAssignment;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence adapter for per-request approval assignments. Used only by the approval module. */
public interface ApprovalAssignmentRepository extends JpaRepository<ApprovalAssignment, UUID> {

    Optional<ApprovalAssignment> findByTenantIdAndRequestId(UUID tenantId, UUID requestId);

    boolean existsByRequestId(UUID requestId);

    List<ApprovalAssignment> findAllByDueAtBefore(Instant now);
}
