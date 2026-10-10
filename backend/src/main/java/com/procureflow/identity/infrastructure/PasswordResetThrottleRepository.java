package com.procureflow.identity.infrastructure;

import com.procureflow.identity.domain.PasswordResetThrottle;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Persistence adapter for the reset throttle ledger. Used only by the identity module. */
public interface PasswordResetThrottleRepository extends JpaRepository<PasswordResetThrottle, UUID> {

    long countByBucketAndRequestedAtAfter(String bucket, Instant since);

    @Modifying
    @Query("delete from PasswordResetThrottle t where t.requestedAt < :before")
    int deleteOlderThan(@Param("before") Instant before);
}
