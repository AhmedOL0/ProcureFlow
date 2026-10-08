package com.procureflow.ai.application;

/**
 * Provider-neutral port for text completion. Business use cases depend on this
 * interface only; the Groq adapter in {@code ai.infrastructure} is the sole
 * implementation and arrives in Phase 6. No implementation exists yet on
 * purpose: nothing may call an AI provider until prompts, validation, auth
 * and usage tracking are in place.
 */
public interface AiProvider {

    AiCompletionResult complete(AiCompletionRequest request);

    /**
     * False until the provider is configured ({@code AI_ENABLED=true} plus a
     * Groq key). Use cases must degrade gracefully when unavailable.
     */
    boolean isAvailable();
}
