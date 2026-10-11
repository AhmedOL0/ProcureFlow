package com.procureflow.identity.infrastructure;

import com.procureflow.identity.domain.AuthThrottle;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Persistence adapter for the auth throttle ledger. Used only by the identity module. */
public interface AuthThrottleRepository extends JpaRepository<AuthThrottle, UUID> {

    long countByBucketAndRequestedAtAfter(String bucket, Instant since);

    @Modifying
    @Query("delete from AuthThrottle t where t.requestedAt < :before")
    int deleteOlderThan(@Param("before") Instant before);
}
