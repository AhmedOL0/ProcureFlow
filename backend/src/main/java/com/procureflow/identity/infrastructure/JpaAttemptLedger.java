package com.procureflow.identity.infrastructure;

import com.procureflow.identity.application.AttemptLedger;
import com.procureflow.identity.domain.AuthThrottle;
import java.time.Instant;
import org.springframework.stereotype.Component;

/** JPA adapter for the auth throttle ledger. */
@Component
public class JpaAttemptLedger implements AttemptLedger {

    private final AuthThrottleRepository throttle;

    public JpaAttemptLedger(AuthThrottleRepository throttle) {
        this.throttle = throttle;
    }

    @Override
    public void record(String bucket, Instant at) {
        throttle.save(new AuthThrottle(bucket, at));
    }

    @Override
    public long countSince(String bucket, Instant since) {
        return throttle.countByBucketAndRequestedAtAfter(bucket, since);
    }

    @Override
    public void pruneBefore(Instant before) {
        throttle.deleteOlderThan(before);
    }
}
