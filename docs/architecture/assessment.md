# Architecture assessment (Stage A, 2026-10-11)

Read-only assessment of the actual repository at `feature/frontend-operations`
(`0b025f5`). No code changed. Backend `./mvnw -B verify` re-run for this
assessment: 123 integration tests + 24 unit `@Test`s + 4 ArchUnit rules,
0 failures, JaCoCo floors met. Claims below re-verified against current
sources, not prior reports.

## 1. Current architecture and module boundaries (Verified)

Twelve business modules under `com.procureflow.<module>/` with
`{domain,application,infrastructure,api}` layers, plus
`shared/{kernel,multitenancy,web}`: identity, organization, supplier,
procurement, approval, budget, purchaseorder, invoice, notification,
audit, analytics, ai. Single deployable jar; Flyway V1–V15; Testcontainers
suites per class. Frontend `src/app/{core,features,layout,shared}` with lazy
routes — already the structure §7 of the mission sketches, so no
reorganization is proposed.

A full import-level edge scan of `backend/src/main` (every `import
com.procureflow.<module>.<layer>` qualified by source layer) shows:

- **Zero `api → infrastructure` edges** and **zero `application →
  foreign infrastructure` edges.** No controller or service reaches into
  another module's repositories. The 4 ArchUnit rules
  (`ArchitectureRulesTest.java:20-43`) hold in practice, not just in CI.
- **All `application → application` edges are port interfaces**
  (`SupplierLookup`, `SupplierOrderUsage`, `BudgetReservationPort`,
  `RequestDecisionPort`, `OrderSourcePort`, `InvoiceOrderPort`,
  `*StatsPort`, `TenantProvisioning`, `UserLookup`) consumed with
  id-references via `getReference` — verified at both ends, including
  `AnalyticsService.java:3-7` importing only `*StatsPort`s.
- **Controllers are thin.** Spot-checked `ApprovalController.decide`
  (`approval/api/ApprovalController.java:53-64`): derives one boolean from
  the principal, delegates to `approvals.decide(...)`, maps to a record
  DTO. No business branching in the web layer observed.
- **Domain is Spring-free** (ArchUnit-enforced); entities carry genuine
  invariants (e.g. `PurchaseRequest.editable()`, contact-primary rules,
  immutable decisions); money paths lock rows (`SELECT FOR UPDATE` in 4
  repositories) inside explicit `@Transactional` use cases with two-thread
  race tests.

## 2. Strengths to preserve

1. Port-based module contact with id-references — do not "simplify" into direct entity sharing.
2. Tenant-check-before-permission with 404s + ~13 cross-tenant negative tests.
3. Decision/budget/order/invoice atomicity: `ApprovalService.decide` flips status, reserves budget, notifies and audits in one orchestrated unit (event listeners `AFTER_COMMIT` + fresh tx so observer failure never rolls back business work).
4. Idempotent request creation (`REQUIRES_NEW` isolated insert + unique key + replay).
5. Append-only Flyway with `ddl-auto=validate` everywhere; per-class Testcontainers with digest-pinned Postgres.
6. Frontend: typed OpenAPI-generated contracts, Signals + RxJS split, all four data states per screen, backend-authoritative authz.

## 3. Dependency violations and coupling (verified)

- **One logical cycle: supplier ⇆ purchaseorder.** `SupplierService.java:5`
  imports `purchaseorder.application.SupplierOrderUsage` (delete guard), while
  purchaseorder imports `SupplierLookup` from supplier. Both directions are
  port interfaces, so there is no build cycle and no transaction spanning —
  but the modules are not strictly layered. Risk: low. Action: none required;
  optional direction fix is to move `SupplierOrderUsage` into supplier (Stage E
  candidate, not a defect).
- **Cross-domain entity references** (`User↔Tenant`, `PurchaseRequest→Supplier`,
  `PurchaseRequestItem→Supplier`, `Membership→User`) exist solely for
  `getReference` id-association — the documented pattern, ArchUnit-clean.
  No lazy traversal across modules observed (OSIV off, `@EntityGraph` where
  controllers read across associations). No action.
- **Infrastructure cross-wiring** is limited to `SecurityConfig` and
  `TenantFilter` (auth chain construction) — legitimate composition root.
  No action.
- `shared` has zero business-logic leakage observed (kernel: `DomainEvent`,
  `TenantContext`; web: envelope/handler). The "dumping ground" risk has
  not materialized. No action.

## 4. Duplicated logic / competing sources of truth (verified)

- **Throttle ledgers are near-duplicates**: `password_reset_throttle` (V13)
  and `auth_throttle` (V14) share shape, repository idiom, and
  record-count-prune logic in two services. Verified duplication, small
  blast radius. Proposal: extract one `ThrottleLedger` port + `check()`
  policy object reused by both (Stage C pilot adjacent, S effort).
  Acceptance: `AccountIT` + `AuthFlowIT` throttle tests green, one table
  per flow or one shared table with namespaced buckets — decision with
  trade-offs documented in the pilot.
- **No duplicated business rules** found across modules (budget math,
  match logic, and totals each live in exactly one service). No action.

## 5. Security, tenant-isolation, transaction risks (verified)

Covered in depth by `docs/enterprise-readiness/02-security-and-tenant-isolation.md`;
architecture-relevant entries:

- Scheduler sweep is tenant-unscoped at the workflow lookup (fixed this
  cycle with a scoped finder + regression test; the sweep query itself is
  intentionally global and indexed).
- Reads on suppliers/departments/requests rest on convention + service
  scoping rather than authority strings — no per-route authority matrix
  test exists. Proposed ArchUnit-adjacent gate (Stage D).
- No outbox: `AFTER_COMMIT` + catch-and-log favors availability; audit rows
  can drop silently. Adopt an outbox only if compliance demands zero-gap
  audit (decision recorded as opt-in, Stage D).
- Notifications have exactly two producers and a logging-only transport —
  honestly labeled, not a hidden stub.

## 6. Missing architectural tests

1. Per-route `@PreAuthorize` presence/coverage matrix (controllers gain
   endpoints faster than reviewers check them). Proposed: ArchUnit rule
   (public controller methods must carry authorization or be allowlisted)
   + an IT matrix asserting 401/403/404 per role on key routes.
2. Port-direction rule: forbid `application → foreign domain entities`
   except `getReference` call sites (allowlist), and forbid any new
   `application → foreign infrastructure` edge (currently zero — lock it
   in while it holds).
3. Conditional-bean boot guard: the `GroqAiProvider` two-constructor crash
   (this cycle) proved the suite never boots conditional beans. Proposed:
   a slice test that instantiates each `@ConditionalOnProperty` bean, or a
   documented manual boot check in the release checklist.
4. Query-count assertions for the four N+1 fan-outs (budget sums, invoice
   lines, approval steps, analytics aggregation) to freeze current behavior
   before bulk-fetch rewrites.

## 7. Prioritized refactoring plan (Stages B–G)

- **Stage B — boundary definition (no code):** ratify the port-direction
  rule + supplier/purchaseorder cycle disposition + throttle-unification
  shape. Risk: none. Acceptance: this assessment's §6 list agreed or amended.
- **Stage C — pilot refactor (S):** unify the two throttle ledgers behind
  one port; add the two missing ArchUnit rules from §6.1–6.2. Risk: low
  (mechanical, covered by `AccountIT`/`AuthFlowIT`). Acceptance: full
  `verify` green, ledgers documented in `docs/architecture/`.
- **Stage D — security & integrity (S–M):** per-route auth matrix test;
  login-throttle follow-ups if probes demand; outbox decision (adopt or
  formally defer with rationale recorded in an ADR). Risk: medium (touches
  security posture). Acceptance: matrix green, ADR-010 (outbox) written.
- **Stage E — incremental adoption (S each):** apply the port-direction
  rule repo-wide; optional `SupplierOrderUsage` move; N+1 bulk-fetch
  rewrites behind query-count tests; pagination envelopes per the
  documented target. Risk: medium (touches hot paths). Each ships with its
  race/load evidence.
- **Stage F — frontend alignment:** no structural change proposed — the
  tree already matches the target shape. Only targeted items: axe CI,
  Firefox CI, api-types regenerate-and-diff job, `valueChanges`
  unsubscribe hygiene. Risk: low.
- **Stage G — verification:** full gates + container boot with AI enabled
  (regression for this cycle's crash) + docs sync (`docs/architecture/`,
  ADRs 010+, `enterprise-readiness` updates).

## 8. What is explicitly out of scope

Microservices, Kafka/RabbitMQ traffic, Kubernetes, per-tenant schemas,
read-model/CQRS split, rich-domain rewrites of CRUD modules, and any new
ports/adapters/events without a demonstrated coordination problem. The
simplest architecture that protects the rules above is the current one,
plus the six tests/rules in §6 and the throttle unification in §7-C.
