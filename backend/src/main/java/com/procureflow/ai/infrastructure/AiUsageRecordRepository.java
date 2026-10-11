package com.procureflow.ai.infrastructure;

import com.procureflow.ai.domain.AiUsageRecord;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Persistence adapter for metered AI calls. Writes only; reads serve the usage endpoint. */
public interface AiUsageRecordRepository extends JpaRepository<AiUsageRecord, UUID> {

    List<AiUsageRecord> findAllByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    @Query("select coalesce(sum(u.promptTokens + u.completionTokens), 0) from AiUsageRecord u"
            + " where u.tenantId = :tenantId and u.createdAt >= :since")
    long sumTokensSince(@Param("tenantId") UUID tenantId, @Param("since") Instant since);
}
