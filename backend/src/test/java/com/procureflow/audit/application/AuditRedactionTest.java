package com.procureflow.audit.application;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The audit backstop masks secret-naming keys even when a producer slips.
 * Pure unit test: no Spring, no containers.
 */
class AuditRedactionTest {

    @Test
    void secretNamingKeysAreMasked() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", "APPROVED");
        payload.put("amountMinor", 1000);
        payload.put("passwordHash", "nope");
        payload.put("refreshToken", "nope");
        payload.put("api_key", "nope");
        payload.put("Authorization", "nope");

        Map<String, Object> safe = AuditService.redacted(payload);

        assertEquals("APPROVED", safe.get("status"));
        assertEquals(1000, safe.get("amountMinor"));
        assertEquals("[REDACTED]", safe.get("passwordHash"));
        assertEquals("[REDACTED]", safe.get("refreshToken"));
        assertEquals("[REDACTED]", safe.get("api_key"));
        assertEquals("[REDACTED]", safe.get("Authorization"));
    }

    @Test
    void ordinaryBusinessKeysPassThrough() {
        Map<String, Object> payload = Map.of(
                "name", "Engineering",
                "period", "2026-10",
                "deciderId", "123",
                "comment", "looks good");

        assertEquals(payload, AuditService.redacted(payload));
    }
}
