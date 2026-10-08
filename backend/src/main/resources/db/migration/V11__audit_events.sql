-- ============================================================
-- V11: audit trail (Phase 5, epic 12 slice 1).
-- Append-only: rows are written by the audit module reacting to domain
-- events after the business transaction commits, and no API mutates or
-- removes them. before/after carry JSON snapshots without secrets.
-- ============================================================

CREATE TABLE audit_events (
    id          UUID         PRIMARY KEY,
    tenant_id   UUID         NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    actor_id    UUID         NOT NULL REFERENCES users (id),
    action      VARCHAR(100) NOT NULL,
    entity_type VARCHAR(60)  NOT NULL,
    entity_id   UUID,
    before_json TEXT,
    after_json  TEXT,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_audit_events_tenant_id ON audit_events (tenant_id, created_at DESC);
CREATE INDEX ix_audit_events_entity ON audit_events (tenant_id, entity_type, entity_id);
