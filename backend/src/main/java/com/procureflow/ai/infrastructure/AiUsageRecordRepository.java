package com.procureflow.ai.infrastructure;

import com.procureflow.ai.domain.AiUsageRecord;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence adapter for metered AI calls. Writes only; reads serve the usage endpoint. */
public interface AiUsageRecordRepository extends JpaRepository<AiUsageRecord, UUID> {

    List<AiUsageRecord> findAllByTenantIdOrderByCreatedAtDesc(UUID tenantId);
}
