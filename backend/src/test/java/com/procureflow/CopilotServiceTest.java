package com.procureflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import com.procureflow.ai.application.AiProvider;
import com.procureflow.ai.application.CopilotService;
import com.procureflow.shared.web.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;

/**
 * Copilot with no provider available: every use case answers 503 before
 * touching analytics, metering or the network. Covers both production
 * shapes: no provider bean at all, and a provider reporting unavailable.
 */
class CopilotServiceTest {

    @Test
    void chatWithoutProviderBeanIsUnavailable() {
        CopilotService copilot = new CopilotService(emptyProviders(), null, null, null, null, null);
        assertUnavailable(() -> copilot.chat("acme", "How much?"));
    }

    @Test
    void extractWithUnavailableProviderIsUnavailable() {
        CopilotService copilot = new CopilotService(
                singletonProviders(new AiProvider() {
                    @Override
                    public com.procureflow.ai.application.AiCompletionResult complete(
                            com.procureflow.ai.application.AiCompletionRequest request) {
                        return fail("must never reach the provider");
                    }

                    @Override
                    public boolean isAvailable() {
                        return false;
                    }
                }),
                null, null, null, null, null);
        assertUnavailable(() -> copilot.extract("acme", "20 laptops"));
    }

    private static ObjectProvider<AiProvider> emptyProviders() {
        return new ObjectProvider<>() {
            @Override
            public AiProvider getIfAvailable() {
                return null;
            }

            @Override
            public AiProvider getIfUnique() {
                return null;
            }

            @Override
            public AiProvider getObject() {
                return fail("no provider bean");
            }

            @Override
            public AiProvider getObject(Object... args) {
                return fail("no provider bean");
            }
        };
    }

    private static ObjectProvider<AiProvider> singletonProviders(AiProvider provider) {
        return new ObjectProvider<>() {
            @Override
            public AiProvider getIfAvailable() {
                return provider;
            }

            @Override
            public AiProvider getIfUnique() {
                return provider;
            }

            @Override
            public AiProvider getObject() {
                return provider;
            }

            @Override
            public AiProvider getObject(Object... args) {
                return provider;
            }
        };
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
