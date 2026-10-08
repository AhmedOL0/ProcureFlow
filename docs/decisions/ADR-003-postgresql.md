# ADR-003: PostgreSQL as primary database

- Status: Accepted (Phase 1)
- Context: Relational procurement data (orders, budgets, approvals) with
  strong consistency needs; JSON pockets for flexible supplier attributes.
- Decision: PostgreSQL 16 as the single system of record. UUID primary
  keys, `timestamptz`, Flyway migrations, `ddl-auto=validate` everywhere
  including dev.
- Consequences: One database to operate and back up. No polyglot
  persistence until a measured need appears; Redis stays a cache, never
  a source of truth.
