# Release readiness checklist

Three gates: **Demo** (recruiters/portfolio today) → **Test env** (shared, friendly users) → **First paying tenant**. Check each box with evidence, not vibes.

## Demo gate (today: PASS with notes)

- [x] Full procure-to-pay journey runs live (register → paid + admin/audit) — verified by screenshots + e2e.
- [x] Backend `verify` + frontend build/vitest/e2e green — 2026-10-10 numbers in `05`.
- [x] No fake data/endpoints; stubs labeled — audit `§0` domain report.
- [ ] README counts fixed (113→127, 24→28) — 10-minute doc fix, do before sending links.
- [ ] Demo reset procedure rehearsed (`docs/demo.md:28-58`).

## Test-env gate (friendly users, non-sensitive data)

- [ ] P0 security closed: login throttling, placeholder-secret refusal, join policy documented or invite-gated.
- [ ] Backup sidecar running + one restore rehearsal recorded.
- [ ] Versioned image tags (no `:latest` ambiguity); rollback = previous tag.
- [ ] Prometheus + Alertmanager receiver firing on synthetic 5xx drill.
- [ ] Backend/frontend healthchecks; `depends_on` conditions.
- [ ] Brevo live proof: reset email received in a real mailbox.
- [ ] Known-limitation page shared with users (single currency, no pagination yet, AI metering without caps).
- [ ] Incident contact + runbook skeleton (who gets paged, where logs live).

## First-tenant gate (real data, real money-adjacent)

- [ ] All P0/P1 roadmap items in `07` Phase 1 shipped with tests.
- [ ] Pagination on audit/notifications/requests/suppliers; 10k-row page <500ms.
- [ ] Suspend/disable + role-update + email verification live and tested.
- [ ] Authority-matrix test + invoice/order race tests green in CI.
- [ ] axe CI + Firefox CI green; no critical a11y violations.
- [ ] Outbox or reconciled audit delivery (no silent gaps).
- [ ] Load test: month-end shape (budgets × reservations × invoices) measured, pool/timeouts tuned.
- [ ] Security re-review: throttling verified by probe, headers verified, dependency scan clean, secrets rotation documented.
- [ ] DPA-friendly posture: retention policy written, export/delete path exercised, subprocessor (Groq/Brevo) terms acknowledged.

## Never-ship red lines

- Open slug join + unthrottled login + no backups + `:latest` images + known 500s = do not expose beyond local demo. Every one of these has a concrete fix above; none requires re-architecture.
