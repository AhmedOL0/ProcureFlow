package com.procureflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.procureflow.ai.application.AiCompletionRequest;
import com.procureflow.ai.application.AiCompletionResult;
import com.procureflow.ai.application.AiProvider;
import com.procureflow.ai.application.AiValidator;
import com.procureflow.ai.application.CopilotService;
import com.procureflow.ai.infrastructure.AiUsageRecordRepository;
import com.procureflow.organization.application.TenantProvisioning;
import com.procureflow.shared.web.ApiException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.UUID;
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
        CopilotService copilot = new CopilotService(emptyProviders(), null, null, null, null, null, 1_000_000L);
        assertUnavailable(() -> copilot.chat("acme", "How much?"));
    }

    @Test
    void extractOverQuotaNeverReachesTheProvider() {
        AiUsageRecordRepository usage = mock(AiUsageRecordRepository.class);
        when(usage.sumTokensSince(any(), any())).thenReturn(1_000_000L);
        TenantProvisioning tenants = mock(TenantProvisioning.class);
        when(tenants.requireTenantId("acme")).thenReturn(UUID.randomUUID());
        CopilotService copilot = new CopilotService(
                singletonProviders(failingProvider()),
                null,
                usage,
                tenants,
                new ObjectMapper(),
                mock(MeterRegistry.class),
                1_000_000L);
        try {
            copilot.extract("acme", "25 ergonomic chairs for the Berlin office");
            fail("expected AI_QUOTA_EXCEEDED");
        } catch (ApiException e) {
            assertEquals(HttpStatus.TOO_MANY_REQUESTS, e.getStatus());
            assertEquals("AI_QUOTA_EXCEEDED", e.getCode());
        }
    }

    @Test
    void extractUnderQuotaReturnsDraft() {
        AiUsageRecordRepository usage = mock(AiUsageRecordRepository.class);
        when(usage.sumTokensSince(any(), any())).thenReturn(0L);
        TenantProvisioning tenants = mock(TenantProvisioning.class);
        when(tenants.requireTenantId("acme")).thenReturn(UUID.randomUUID());
        MeterRegistry meters = mock(MeterRegistry.class);
        when(meters.counter(eq("procureflow.ai.calls"), any(String[].class))).thenReturn(mock(Counter.class));
        CopilotService copilot = new CopilotService(
                singletonProviders(new AiProvider() {
                    @Override
                    public AiCompletionResult complete(AiCompletionRequest request) {
                        return new AiCompletionResult(
                                """
                                {"title": "Laptops", "priority": "HIGH",
                                 "items": [{"description": "ThinkPad", "quantity": 20, "unitPriceMinor": 89900}]}""",
                                "fake-test",
                                12,
                                30);
                    }

                    @Override
                    public boolean isAvailable() {
                        return true;
                    }
                }),
                null,
                usage,
                tenants,
                new ObjectMapper(),
                meters,
                1_000_000L);
        AiValidator.DraftRequest draft = copilot.extract("acme", "25 ergonomic chairs for the Berlin office");
        assertEquals("Laptops", draft.title());
        assertEquals(1, draft.items().size());
    }

    private static AiProvider failingProvider() {
        return new AiProvider() {
            @Override
            public AiCompletionResult complete(AiCompletionRequest request) {
                return fail("must never reach the provider");
            }

            @Override
            public boolean isAvailable() {
                return true;
            }
        };
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
                null, null, null, null, null, 1_000_000L);
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
