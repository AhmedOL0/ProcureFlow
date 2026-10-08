# ADR-005: Groq behind an AI provider abstraction

- Status: Accepted (Phase 1)
- Context: Procurement copilot and analytics explanations need an LLM, but
  provider lock-in and key sprawl are real risks.
- Decision: All AI flows through the `ai` module's `AiProvider` port.
  Groq is the first adapter (env-keyed, disabled by default); prompts are
  server-side, responses validated, usage metered per tenant. The frontend
  never holds keys and never calls Groq directly.
- Consequences: Swapping providers means writing one adapter, not
  rewriting features. AI stays unavailable until Phase 6 wiring lands.
