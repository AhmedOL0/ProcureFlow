-- ============================================================
-- V8: in-app notifications (Phase 4, epic 9 slice 1).
-- Rows are written by the notification module reacting to domain events
-- (request submitted / decided) after the business transaction commits,
-- so a notification failure can never roll back business work. Recipient
-- is the requester; the read flag drives the inbox. Email stays a
-- logging skeleton (no SMTP) until a real transport lands.
-- ============================================================

CREATE TABLE notifications (
    id          UUID        PRIMARY KEY,
    tenant_id   UUID        NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    user_id     UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    type        VARCHAR(40) NOT NULL,
    title       VARCHAR(200) NOT NULL,
    body        TEXT,
    read        BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_notifications_tenant_user ON notifications (tenant_id, user_id, created_at DESC);
