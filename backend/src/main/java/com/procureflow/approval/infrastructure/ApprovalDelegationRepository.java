package com.procureflow.approval.infrastructure;

import com.procureflow.approval.domain.ApprovalDelegation;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Persistence adapter for approval delegations. Used only by the approval module. */
public interface ApprovalDelegationRepository extends JpaRepository<ApprovalDelegation, UUID> {

    Optional<ApprovalDelegation> findByIdAndTenantId(UUID id, UUID tenantId);

    List<ApprovalDelegation> findAllByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    @Query("select d from ApprovalDelegation d where d.tenantId = :tenantId"
            + " and d.delegateId = :delegateId and d.startsAt <= :now and d.endsAt > :now")
    List<ApprovalDelegation> findActiveForDelegate(
            @Param("tenantId") UUID tenantId, @Param("delegateId") UUID delegateId, @Param("now") Instant now);
}
