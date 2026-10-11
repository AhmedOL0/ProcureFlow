package com.procureflow.identity.application;

import java.time.Instant;

/**
 * Narrow port over a throttle ledger (record attempts, count recent ones,
 * prune old ones) so the throttle policy unit-tests against an in-memory
 * fake instead of a database.
 */
public interface AttemptLedger {

    void record(String bucket, Instant at);

    long countSince(String bucket, Instant since);

    void pruneBefore(Instant before);
}
