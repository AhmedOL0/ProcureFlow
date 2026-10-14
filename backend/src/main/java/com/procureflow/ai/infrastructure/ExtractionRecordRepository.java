package com.procureflow.ai.infrastructure;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * Extraction history reads. Every query carries the tenant slug: absent
 * rows (wrong tenant, unknown id) answer 404 upstream, never 403, matching
 * the relational tenant-before-permission convention.
 */
public interface ExtractionRecordRepository extends MongoRepository<ExtractionRecord, String> {

    Page<ExtractionRecord> findByTenantSlugOrderByCreatedAtDesc(String tenantSlug, Pageable pageable);

    Optional<ExtractionRecord> findByIdAndTenantSlug(String id, String tenantSlug);

    long countByTenantSlug(String tenantSlug);
}
