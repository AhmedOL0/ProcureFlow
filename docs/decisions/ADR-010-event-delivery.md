# ADR-010: Event delivery without an outbox (deferred)

- Status: Accepted (architecture assessment, Stage B decision)
- Context: Domain changes publish `PurchaseRequestSubmitted`,
  `PurchaseRequestDecided` and `AuditTrailLogged`; listeners persist
  notifications and audit rows `AFTER_COMMIT` in fresh transactions with
  catch-and-log. A crash between commit and listener drops a row. A
  transactional outbox (table + relay + dedupe + monitoring) would close
  the gap at the cost of a new writer, a relay job, and operational
  surface — with RabbitMQ configured but unwired and a single deployable.
- Decision:
  - Keep in-process Spring events; do NOT add an outbox or a broker
    now. Availability-first stands: observer failure never rolls back
    business work, and the gap is a dropped notification/audit row, not
    a corrupt ledger (money moves inside the business transaction).
  - Revisit triggers (any one reopens this ADR): a compliance customer
    requiring zero-gap audit, a second consumer that must not miss
    events, or measured event loss in production.
  - If adopted later: `outbox` table written in the business transaction,
    relay publishing to the existing domain events, consumer idempotency
    on `eventId`, and a relay-lag metric next to the `alerts.yml` set.
- Consequences: No new infrastructure; audit stays best-effort with the
  documented drop-on-failure behavior (`AuditService` catch-and-log).
  The release checklist must not claim lossless audit until this ADR is
  revisited.
