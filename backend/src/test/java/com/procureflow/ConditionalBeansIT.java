package com.procureflow;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.procureflow.ai.application.AiProvider;
import com.procureflow.ai.application.CopilotService;
import com.procureflow.ai.infrastructure.GroqAiProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;

/**
 * Production-shape wiring the suite otherwise never boots: with
 * {@code AI_ENABLED=true} the conditional provider bean must exist and the
 * copilot must wire against it. A two-constructor provider once crash-looped
 * the deployed container while every suite stayed green — this test fails
 * the build instead. No provider call is made, so no network or key needed.
 */
@TestPropertySource(properties = {"app.ai.enabled=true", "app.ai.groq.api-key=dummy-test-key"})
class ConditionalBeansIT extends AbstractIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = postgresContainer();

    @Autowired
    private ApplicationContext context;

    @Test
    void aiEnabledBootsProviderAndCopilot() {
        assertTrue(context.getBeansOfType(GroqAiProvider.class).size() == 1);
        assertTrue(context.getBeansOfType(AiProvider.class).size() == 1);
        assertNotNull(context.getBean(CopilotService.class));
    }
}
