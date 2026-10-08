package com.procureflow.approval.infrastructure;

import com.procureflow.approval.domain.ApprovalStep;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence adapter for approval steps. Used only by the approval module. */
public interface ApprovalStepRepository extends JpaRepository<ApprovalStep, UUID> {

    List<ApprovalStep> findAllByWorkflowIdOrderByStepOrderAsc(UUID workflowId);

    Optional<ApprovalStep> findByWorkflowIdAndStepOrder(UUID workflowId, int stepOrder);

    boolean existsByWorkflowIdAndStepOrder(UUID workflowId, int stepOrder);

    void deleteByIdAndWorkflowId(UUID id, UUID workflowId);
}
