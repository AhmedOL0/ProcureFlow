# AI live probes (real Groq, local stack)

Systematic pass over all four copilot use cases against compose `:8081`
with a real key (`AI_ENABLED=true`). Single run, tiny cost (~1.4k of the
1M monthly cap); probe tenant `aiprobe-001` created, exercised, then
fully dropped (PG tenant/users/roles/usage + 1 mongo doc, all verified
zero afterwards). Re-run after any prompt/validator change and compare.

## Procedure

Register `aiprobe-<n>` (12+ char password), then one call per use case
with client-side timing; read `/ai/usage` for metering; delete the
tenant afterwards (users, roles, `ai_usage`, tenant row + extraction
docs — same dependency order as `docs/demo.md` reset).

## Results (2026-10-14, `openai/gpt-oss-120b`)

| Use case | Latency | Verdict |
|---|---|---|
| chat "How much did we order?" | 1681 ms | Cited answer, figures match fresh-tenant zeros (requested/ordered/invoiced/paid = 0) |
| extract "20 laptops please, high priority" | 776 ms | Valid shape: Laptops/HIGH/20x, price 0 (validator-legal; server re-validates on create) |
| compare Acme 100000 vs Globex 95000 | 1824 ms | Correct winner + spread, no hallucinated third quote |
| explain-spend all time | 1191 ms | Coherent zero narrative, no invented figures |

Metering: 4 calls, 617 prompt / 812 completion tokens, per-feature
split exact (`explain` 151/321, `compare` 148/204, `extract` 167/198,
chat remainder). No validation rejections, no 5xx, no prompt leakage
(prompts carry aggregates only — unchanged).

## Limits (stated)

One run, one key, empty tenant: proves the live path end to end, not
quality at scale. No adversarial/prompt-injection battery (validator
unit tests cover the hostile-input contract instead). Re-probe on
model or prompt changes (`Prompts.VERSION` bump).
