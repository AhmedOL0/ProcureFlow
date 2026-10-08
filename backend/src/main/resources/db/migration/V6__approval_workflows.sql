-- ============================================================
-- V6: approval workflows, steps and per-request assignments (Phase 4,
-- epic 5 slice 2). A workflow matches requests by total amount; its
-- ordered steps name the approvers. An assignment pins the request to
-- its current step with a due date; overdue steps escalate to the next
-- level. No assignment (no matching workflow) keeps the slice-1 rule:
-- any approver or active delegate may decide.
-- ============================================================

CREATE TABLE approval_workflows (
    id                  UUID         PRIMARY KEY,
    tenant_id           UUID         NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    name                VARCHAR(200) NOT NULL,
    min_amount_minor    BIGINT       NOT NULL DEFAULT 0,
    max_amount_minor    BIGINT,
    escalate_after_days INTEGER      NOT NULL DEFAULT 3,
    active              BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_approval_workflows_days CHECK (escalate_after_days >= 1),
    CONSTRAINT ck_approval_workflows_range CHECK (max_amount_minor IS NULL OR max_amount_minor > min_amount_minor)
);
CREATE INDEX ix_approval_workflows_tenant_id ON approval_workflows (tenant_id);

CREATE TABLE approval_steps (
    id          UUID        PRIMARY KEY,
    workflow_id UUID        NOT NULL REFERENCES approval_workflows (id) ON DELETE CASCADE,
    step_order  INTEGER     NOT NULL,
    approver_id UUID        NOT NULL REFERENCES users (id),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_approval_steps_order CHECK (step_order >= 1),
    CONSTRAINT uq_approval_steps_workflow_order UNIQUE (workflow_id, step_order)
);
CREATE INDEX ix_approval_steps_workflow_id ON approval_steps (workflow_id);

CREATE TABLE approval_assignments (
    id                 UUID        PRIMARY KEY,
    tenant_id          UUID        NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    request_id         UUID        NOT NULL REFERENCES purchase_requests (id) ON DELETE CASCADE,
    workflow_id        UUID        NOT NULL REFERENCES approval_workflows (id),
    current_step_order INTEGER     NOT NULL,
    due_at             TIMESTAMPTZ NOT NULL,
    escalated          BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_approval_assignments_request UNIQUE (request_id)
);
CREATE INDEX ix_approval_assignments_tenant_id ON approval_assignments (tenant_id);
CREATE INDEX ix_approval_assignments_due_at ON approval_assignments (due_at);
