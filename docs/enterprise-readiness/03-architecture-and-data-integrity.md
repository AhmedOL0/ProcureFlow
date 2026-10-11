# Architecture & data integrity

## Module boundaries (verified)

Twelve modules under `com.procureflow.<module>/{domain,application,infrastructure,api}` plus `shared/{kernel,multitenancy,web}`. Four ArchUnit rules gate it (`ArchitectureRulesTest.java:20-43`): api→infrastructure banned, shared→modules banned, no field injection, domain→Spring banned — all green this session.

Cross-module contact observed is disciplined: application ports exchanging ids/DTOs (`SupplierLookup`, `BudgetReservationPort`, `RequestDecisionPort`, `OrderSourcePort`, `InvoiceOrderPort`, stats ports for analytics), entity association by id via `getReference`, and three domain events (`PurchaseRequestSubmitted`, `PurchaseRequestDecided`, `AuditTrailLogged`) consumed `AFTER_COMMIT` in fresh transactions. Controllers are thin (validate → one use case → record DTO). No circular-dependency, oversized-service, or duplicated-domain-logic smell was evidenced; `application→application` port calls are allowed by design and observed used correctly.

Two honest limitations: the ArchUnit suite does not cover `domain→domain` id-refs or per-route authorization (recommended additions in `01`, P1/S), and there is no outbox — crash between commit and `AFTER_COMMIT` listener drops a notification/audit row (P2/M; the catch-and-log in `AuditService.java:45-56` favors availability over completeness — a defensible choice, but it should become an outbox before compliance claims).

## Eventing without a bus (verified)

RabbitMQ and the AMQP starter are configured but unwired (zero `RabbitListener|RabbitTemplate` hits; listeners explicitly deferred in `DomainEvent` javadocs). The actual transport is in-process Spring events. This is correct for the current scale — do not add Kafka/RabbitMQ usage without a second deployable consumer. The compose services for redis/rabbitmq areوبة dead weight today (see `06`); either wire a real use or park them.

## Migrations (verified)

V1–V13 append-only with phase headers; UUID PKs generated in `@PrePersist`; `timestamptz` everywhere; `ddl-auto=validate` in all profiles; unique constraints back every race-sensitive path (idempotency key, decision request, assignment request, order request, reservation request, token hash, invoice number). Money as `BIGINT` minor units + `VARCHAR(3)` currency. No `EAGER`, no JPA cascades (deletes live in DDL + explicit service guards).

## Gaps with evidence

1. **No pagination** — zero `Pageable` hits; every list unbounded. The first scalability wall. P0/M.
2. **Missing indexes**: `budgets(tenant,period)` (serves `lockPeriodBudgets`), `purchase_requests(tenant,status)`, `supplier_category_links(category_id)` reverse leg, `invoice_lines.order_item_id` has an index but **no FK** (`V10:30`) — orphan risk if line deletion ever lands. P1/S via a V14 migration. The scheduler's `ix_due_at` is correctly present.
3. **N+1 fan-outs**: `BudgetService.java:67-68` per-pot sums, `InvoiceService.java:80-93` per-line sums, `OrderService.java:137-149` per-line reads, `ApprovalService.java:182-191` per-workflow step fetches, analytics in-memory aggregation. Fine at demo scale; each needs a bulk-fetch rewrite with query-count tests before large tenants. P1/M.
4. **Unproven races**: invoice booking/pay and order receive lock parent rows but carry no two-thread tests (decisions, budget holds, orders-create, idempotent creates, and reset redemption do). P1/S.
5. **Contact-primary race**: read-modify-write `unsetPrimaries` with no unique guard — dual-primary possible under concurrency. P2/S (partial unique index on `(supplier) WHERE primary`).
6. **Connection pool**: `maximum-pool-size: 10` only; no timeouts (statement/lock/query), no leak detection. P2/S.
7. **No retention policy**: audit/notifications/ai_usage/throttle grow forever (throttle prunes opportunistically only). P2/M.
8. ** Hard deletes everywhere** except append-only sets; supplier/budget deletes are guarded (409s verified), the rest cascade silently. Acceptable if documented; add a data-lifecycle note. P3/S (docs).

## What not to change

The modular monolith is the right shape; microservices would add failure modes with zero evidenced need. Redis should stay a cache (never source of truth per ADR-003) and should only be wired for a measured hot path. `Instant`↔`timestamptz` and `@Enumerated(STRING)` are correct as-is.
