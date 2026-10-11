package com.procureflow.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.procureflow.identity.application.AttemptLedger;
import com.procureflow.identity.application.AuthAttemptThrottle;
import com.procureflow.shared.web.ApiException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Throttle policy against an in-memory ledger: limits trip per bucket, and
 * buckets never share a budget. No Spring, no database.
 */
class AuthAttemptThrottleTest {

    private final MemoryLedger ledger = new MemoryLedger();
    private final AuthAttemptThrottle throttle = new AuthAttemptThrottle(ledger);

    @Test
    void loginTripsAfterTenAttemptsOnOneAccount() {
        for (int i = 0; i < 10; i++) {
            throttle.checkLogin("ada@acme.test", "10.0.0.1");
        }
        ApiException throttled = assertThrows(
                ApiException.class, () -> throttle.checkLogin("ada@acme.test", "10.0.0.1"));
        assertEquals(429, throttled.getStatus().value());
        assertEquals("TOO_MANY_REQUESTS", throttled.getCode());
    }

    @Test
    void loginBucketsIsolateAccountsAndIps() {
        for (int i = 0; i < 10; i++) {
            throttle.checkLogin("ada@acme.test", "10.0.0.1");
        }
        // Another account from the same IP still has its own budget.
        throttle.checkLogin("boss@acme.test", "10.0.0.1");
        // Same account from another IP still trips: the account bucket is spent.
        assertThrows(ApiException.class, () -> throttle.checkLogin("ada@acme.test", "10.0.0.2"));
    }

    @Test
    void registerTripsAfterSixtyAttemptsFromOneIp() {
        for (int i = 0; i < 60; i++) {
            throttle.checkRegister("10.0.0.9");
        }
        ApiException throttled =
                assertThrows(ApiException.class, () -> throttle.checkRegister("10.0.0.9"));
        assertEquals("TOO_MANY_REQUESTS", throttled.getCode());
    }

    @Test
    void attemptsRecordHashedBucketsOnly() {
        throttle.checkLogin("Ada@Acme.Test", "10.0.0.3");
        assertTrue(ledger.rows.stream()
                .noneMatch(row -> row.bucket().contains("ada") || row.bucket().contains("10.0.0.3")));
    }

    @Test
    void inviteIssueTripsAfterSixtyPerActor() {
        UUID actor = UUID.randomUUID();
        for (int i = 0; i < 60; i++) {
            throttle.checkInviteIssue(actor);
        }
        ApiException throttled = assertThrows(ApiException.class, () -> throttle.checkInviteIssue(actor));
        assertEquals(429, throttled.getStatus().value());
        assertEquals("TOO_MANY_REQUESTS", throttled.getCode());
        // Another actor keeps a full budget.
        throttle.checkInviteIssue(UUID.randomUUID());
    }

    @Test
    void inviteRedeemTripsAfterThirtyPerIp() {
        for (int i = 0; i < 30; i++) {
            throttle.checkInviteRedeem("10.0.0.7");
        }
        ApiException throttled = assertThrows(ApiException.class, () -> throttle.checkInviteRedeem("10.0.0.7"));
        assertEquals("TOO_MANY_REQUESTS", throttled.getCode());
    }

    @Test
    void verifyRedeemTripsAfterSixtyPerIp() {
        for (int i = 0; i < 60; i++) {
            throttle.checkVerifyRedeem("10.0.0.8");
        }
        ApiException throttled = assertThrows(ApiException.class, () -> throttle.checkVerifyRedeem("10.0.0.8"));
        assertEquals("TOO_MANY_REQUESTS", throttled.getCode());
    }

    @Test
    void adminCreateTripsAfterTwoHundredPerTenant() {
        for (int i = 0; i < 200; i++) {
            throttle.checkAdminCreate("acme");
        }
        ApiException throttled = assertThrows(ApiException.class, () -> throttle.checkAdminCreate("acme"));
        assertEquals("TOO_MANY_REQUESTS", throttled.getCode());
        throttle.checkAdminCreate("globex");
    }

    @Test
    void changePasswordTripsAfterTenPerAccount() {
        UUID user = UUID.randomUUID();
        for (int i = 0; i < 10; i++) {
            throttle.checkChangePassword(user);
        }
        ApiException throttled = assertThrows(ApiException.class, () -> throttle.checkChangePassword(user));
        assertEquals("TOO_MANY_REQUESTS", throttled.getCode());
    }

    private static class MemoryLedger implements AttemptLedger {
        private final List<Entry> rows = new ArrayList<>();

        private record Entry(String bucket, Instant at) {
        }

        @Override
        public void record(String bucket, Instant at) {
            rows.add(new Entry(bucket, at));
        }

        @Override
        public long countSince(String bucket, Instant since) {
            return rows.stream()
                    .filter(row -> row.bucket().equals(bucket) && !row.at().isBefore(since))
                    .count();
        }

        @Override
        public void pruneBefore(Instant before) {
            rows.removeIf(row -> row.at().isBefore(before));
        }
    }
}
