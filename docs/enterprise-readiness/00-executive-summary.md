# Enterprise readiness — executive summary

Audit date: 2026-10-10. Branch `feature/frontend-operations` (includes origin/main: PR #12 account, PR #13 hardening). Read-only audit; no application code changed. Evidence verified by inspection this session; tests re-run (see Verification below).

## Verdict

ProcureFlow is a **strong, honest MVP of a procure-to-pay core** and a **credible portfolio piece**, but it is **not yet enterprise-complete**. An experienced enterprise team would respect the engineering discipline (modular boundaries with ArchUnit gates, tenant-check-before-permission with 404s, pessimistic locking on money paths with race tests, idempotent creation, append-only Flyway, consistent error envelope) and then list concrete gaps before letting a first paying tenant in: no login throttling, open workspace join, unbounded list endpoints, unwired observability/backup, and missing account-lifecycle controls (suspend, role update, invites, email verification).

No fabricated completeness was found: documented stubs (log-mode mail, logging notification transport) are labeled as such, and the UI omits columns with no backing API rather than faking them.

## Strongest capabilities (verified)

- End-to-end procure-to-pay works: draft → submit → lane-routed approve/reject → budget reservation in the same transaction → order → receive → invoice with 3-way match → pay to PAID, with cancel/release semantics. Backend `verify` green: 115 integration tests + 16 unit/ArchUnit checks.
- Money safety is real, not claimed: `SELECT FOR UPDATE` on request/budget/order/invoice rows, unique constraints as backstops, two-thread race tests for decisions, budget holds, orders, idempotent creates, and reset redemption.
- Tenant isolation is service-layer deep with ~13 cross-tenant negative tests answering 404 (never 403). RBAC: 17-permission catalog, 6 roles, `@PreAuthorize` on mutations, frontend hints never trusted.
- Auth is careful: HS512 with boot-enforced ≥64-char secret, opaque single-use refresh with whole-chain revocation on reuse, generic login/reset responses, BCrypt-12, DB-throttled reset with atomic single-use tokens.
- Frontend is a real product surface: lazy routes, typed API services matching backend mappings (spot-checked 8), all four data states on every screen, destructive confirmations, unsaved-draft guard, 19 Playwright journeys green.

## Most serious gaps

1. **No login/refresh/register throttling** (only password-reset is throttled). Unlimited credential stuffing. High.
2. **Open registration joins any known slug as MEMBER** (reads + request rights). No invites, no approval. High for real tenants.
3. **No pagination anywhere** — every list returns unbounded arrays; audit/notifications/ai_usage grow forever. Breaks at scale. High.
4. **Escalation scheduler sweeps tenants with an unscoped workflow lookup** (`ApprovalService findById` without tenant check). UUIDs make exploitation impractical, but it is an unscoped cross-tenant write path. Medium, fix cheap.
5. **No account suspension, role update, or email verification.** Compromised/insider accounts cannot be disabled; roles are immutable after creation. Medium-High for B2B operations.
6. **Access tokens outlive password change/reset/logout** (up to 15 min). No revocation list. Medium.
7. **Observability/backup are configs, not systems**: Prometheus/Grafana unwired, no alerts pipeline, no pg backup/restore procedure, no correlation IDs, no structured logging. Medium.
8. **Missing concurrency tests** on invoice booking/pay and order receive (locks exist, races unproven). Medium.
9. **AI has metering but no limits**: no per-tenant quotas, no 429/retry handling, no prompt-injection sanitization beyond length checks. Medium (cost abuse).
10. **Stale/missing docs**: README test counts stale (113 vs 127, 24 vs 28), no backup runbook, no API error catalog, Terraform is a validated skeleton only.

## Top five immediate actions

1. Throttle login/refresh/register (bucket per IP+account). (Security, S; the placeholder-secret scare was disproven — the default is 61 chars and the boot length gate already refuses it.)
2. Close or gate workspace join: invite-token or admin-approval flow; add suspend/disable + role-update endpoints with tests. (Security/ops, M)
3. Paginate list endpoints (envelope `{content,page,size,totalElements,totalPages}` already documented as the target in `docs/api/README.md`) starting with audit/notifications/requests. (Scalability, M)
4. Scope the scheduler path by tenant (`findByIdAndTenantId`, test) + add the 4 missing composite indexes (budgets tenant+period, requests tenant+status, category-links reverse, invoice-line FK or documented decision). (Correctness/perf, S)
5. Wire the backup story (nightly pg_dump sidecar + restore test) and the Prometheus service + alert pipeline that `alerts.yml` already anticipates. (Ops, M)

## Biggest security concern

Credential-stuffing against unthrottled login combined with open slug join and no account suspension: an attacker can guess passwords freely, enter any guessable workspace, and defenders cannot disable the account — only rotate its password (which still leaves a 15-minute access-token window). Each link is Medium; the chain is the High.

## Biggest reliability concern

Unbounded reads + N+1 fan-outs (budget sums, invoice lines, approval steps, analytics aggregation) with a 10-connection pool and no query timeouts: a large tenant's month-end (budgets × reservations × invoices) is the first realistic outage shape. No load test has ever been run (stated, not measured).

## What was and was not verified

- Verified by execution this session: backend `./mvnw -B verify` (115 IT + 16 unit/ArchUnit, coverage floors met), frontend `npm run build`, 28/28 Vitest, 19/19 Playwright (Chromium, stubbed doubles). Live probes: `GET /users/me` → 200; non-UUID path var → 400 `INVALID_PATH_VARIABLE`.
- Verified by inspection: controllers/services/entities/repos/migrations, security chain, RBAC mapping, test inventory (131 checks counted), CI workflows, compose files, docs inventory, Stitch directory listing + sampled screen comparison.
- Not verified: visual parity with Stitch (no rendering performed), real Groq behavior (no live key used), Brevo delivery (535-blocked, pre-existing), production deployment (never deployed), load/performance numbers (no load test exists).
