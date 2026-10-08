package com.procureflow.ai.application;

/**
 * The validated answer of an AI provider: raw text plus token accounting so
 * every call can be metered and budgeted per tenant.
 */
public record AiCompletionResult(
        String text,
        String model,
        int promptTokens,
        int completionTokens) {
}
