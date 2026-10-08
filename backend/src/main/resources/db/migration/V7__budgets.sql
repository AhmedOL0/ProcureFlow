-- ============================================================
-- V7: budgets and reservations (Phase 4, epic 6 slice 1).
-- A budget is a named pot per tenant and UTC month (YYYY-MM).
-- Approving a request reserves its total against the period budget in
-- the same transaction as the status change; overspend answers 409.
-- Cancelling an approved request releases its reservation. No budget
-- for the period means no governance (approvals proceed unreserved).
-- department_id is reserved: requests carry no department yet, so only
-- tenant-wide (NULL department) budgets match for now.
-- ============================================================

CREATE TABLE budgets (
    id            UUID         PRIMARY KEY,
    tenant_id     UUID         NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    department_id UUID         REFERENCES departments (id),
    name          VARCHAR(200) NOT NULL,
    period        VARCHAR(7)   NOT NULL,
    amount_minor  BIGINT       NOT NULL,
    currency      VARCHAR(3)   NOT NULL DEFAULT 'MAD',
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_budgets_amount CHECK (amount_minor >= 0),
    CONSTRAINT uq_budgets_tenant_period_name UNIQUE (tenant_id, period, name)
);
CREATE INDEX ix_budgets_tenant_id ON budgets (tenant_id);

CREATE TABLE budget_reservations (
    id           UUID        PRIMARY KEY,
    tenant_id    UUID        NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    budget_id    UUID        NOT NULL REFERENCES budgets (id) ON DELETE CASCADE,
    request_id   UUID        NOT NULL REFERENCES purchase_requests (id) ON DELETE CASCADE,
    amount_minor BIGINT      NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_budget_reservations_amount CHECK (amount_minor >= 0),
    CONSTRAINT uq_budget_reservations_request UNIQUE (request_id)
);
CREATE INDEX ix_budget_reservations_budget_id ON budget_reservations (budget_id);
CREATE INDEX ix_budget_reservations_tenant_id ON budget_reservations (tenant_id);
