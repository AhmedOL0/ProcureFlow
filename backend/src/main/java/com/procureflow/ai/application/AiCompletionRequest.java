package com.procureflow.ai.application;

/**
 * A validated completion request. The tenant id and feature name travel with
 * every request so usage can be metered per tenant (see {@link AiUsage}) and
 * prompts can be audited. Prompts are assembled server-side only.
 *
 * @param tenantId   tenant on whose behalf the call runs
 * @param feature    calling feature (for example {@code copilot})
 * @param systemPrompt fixed instructions; never user input
 * @param userPrompt  user-supplied content, length-checked by the caller
 * @param maxTokens   hard cap guarding runaway cost
 * @param temperature sampling temperature chosen per use case
 */
public record AiCompletionRequest(
        String tenantId,
        String feature,
        String systemPrompt,
        String userPrompt,
        int maxTokens,
        double temperature) {
}
