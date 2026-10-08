/**
 * AI orchestration boundary.
 *
 * <p>Owns: the {@code AiProvider} port, prompt construction, request
 * validation, response validation, usage tracking. The Groq implementation
 * lives in {@code infrastructure} and is disabled until Phase 6
 * (AI Procurement Copilot). The frontend never talks to Groq directly;
 * every AI call flows through this module with tenant + permission checks.</p>
 */
package com.procureflow.ai;
