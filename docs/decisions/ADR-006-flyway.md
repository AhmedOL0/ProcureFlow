# ADR-006: Flyway for database migrations

- Status: Accepted (Phase 1)
- Context: Schema must evolve safely across dev, CI, staging and prod with
  full history and reviewable diffs.
- Decision: Flyway, versioned SQL only (`V<n>__description.sql`),
  append-only, validated on migrate. `ddl-auto=validate` everywhere.
- Consequences: Every schema change is a reviewed file; rollbacks are
  forward migrations. Test profile migrates too, so tests run the real chain.
