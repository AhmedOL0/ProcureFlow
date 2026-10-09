package com.procureflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import com.procureflow.ai.application.AiCompletionRequest;
import com.procureflow.ai.application.AiCompletionResult;
import com.procureflow.ai.application.AiProvider;
import com.procureflow.ai.application.CopilotService;
import com.procureflow.shared.web.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/**
 * Copilot with no provider available: every use case answers 503 before
 * touching analytics, metering or the network.
 */
class CopilotServiceTest {

    private final CopilotService copilot = new CopilotService(
            new AiProvider() {
                @Override
                public AiCompletionResult complete(AiCompletionRequest request) {
                    return fail("must never reach the provider");
                }

                @Override
                public boolean isAvailable() {
                    return false;
                }
            },
            null, null, null, null);

    @Test
    void chatWhenDisabledIsUnavailable() {
        assertUnavailable(() -> copilot.chat("acme", "How much?"));
    }

    @Test
    void extractWhenDisabledIsUnavailable() {
        assertUnavailable(() -> copilot.extract("acme", "20 laptops"));
    }

    private void assertUnavailable(Runnable call) {
        try {
            call.run();
            fail("expected AI_DISABLED");
        } catch (ApiException e) {
            assertEquals(HttpStatus.SERVICE_UNAVAILABLE, e.getStatus());
            assertEquals("AI_DISABLED", e.getCode());
        }
    }
}
