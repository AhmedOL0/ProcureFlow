-- ============================================================
-- V5: approval decisions + delegations (Phase 4, epic 5 slice 1).
-- Decisions are immutable: one row per request, no update/delete API.
-- Delegations lend decide power to a non-approver for a bounded period.
-- Workflows/steps (assignment, escalation) arrive in the next slice.
-- ============================================================

CREATE TABLE approval_decisions (
    id          UUID        PRIMARY KEY,
    tenant_id   UUID        NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    request_id  UUID        NOT NULL REFERENCES purchase_requests (id) ON DELETE CASCADE,
    decider_id  UUID        NOT NULL REFERENCES users (id),
    decision    VARCHAR(20) NOT NULL CHECK (decision IN ('APPROVED', 'REJECTED')),
    comment     TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_approval_decisions_request UNIQUE (request_id)
);
CREATE INDEX ix_approval_decisions_tenant_id ON approval_decisions (tenant_id);
CREATE INDEX ix_approval_decisions_decider_id ON approval_decisions (decider_id);

CREATE TABLE approval_delegations (
    id           UUID        PRIMARY KEY,
    tenant_id    UUID        NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    delegator_id UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    delegate_id  UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    starts_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    ends_at      TIMESTAMPTZ NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_approval_delegations_period CHECK (ends_at > starts_at),
    CONSTRAINT ck_approval_delegations_distinct CHECK (delegator_id <> delegate_id)
);
CREATE INDEX ix_approval_delegations_tenant_id ON approval_delegations (tenant_id);
