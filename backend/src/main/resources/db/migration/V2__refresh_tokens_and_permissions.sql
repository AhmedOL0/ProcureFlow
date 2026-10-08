-- ============================================================
-- V2: refresh-token store + global permission catalog.
-- Refresh tokens are opaque: only the SHA-256 hash is persisted.
-- Permissions are global codes; roles bind them per tenant.
-- The codes below mirror PermissionCodes in Java; change both together.
-- ============================================================

CREATE TABLE refresh_tokens (
    id          UUID        PRIMARY KEY,
    user_id     UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash  VARCHAR(64) NOT NULL UNIQUE,
    expires_at  TIMESTAMPTZ NOT NULL,
    revoked     BOOLEAN     NOT NULL DEFAULT FALSE,
    replaced_by UUID        REFERENCES refresh_tokens (id),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_refresh_tokens_user_id ON refresh_tokens (user_id);

INSERT INTO permissions (id, code, description) VALUES
    (gen_random_uuid(), 'tenant:admin', 'Full control over the tenant workspace'),
    (gen_random_uuid(), 'tenant:manage', 'Rename and configure the workspace'),
    (gen_random_uuid(), 'user:manage', 'Create and administer workspace users'),
    (gen_random_uuid(), 'department:manage', 'Manage departments and memberships'),
    (gen_random_uuid(), 'supplier:read', 'View suppliers'),
    (gen_random_uuid(), 'supplier:write', 'Create and edit suppliers'),
    (gen_random_uuid(), 'procurement:request', 'Raise purchase requests'),
    (gen_random_uuid(), 'procurement:approve', 'Approve or reject requests'),
    (gen_random_uuid(), 'budget:read', 'View budgets and spending'),
    (gen_random_uuid(), 'budget:manage', 'Manage budgets and allocations'),
    (gen_random_uuid(), 'order:read', 'View purchase orders'),
    (gen_random_uuid(), 'order:write', 'Create and edit purchase orders'),
    (gen_random_uuid(), 'invoice:read', 'View invoices'),
    (gen_random_uuid(), 'invoice:write', 'Validate invoices and payments'),
    (gen_random_uuid(), 'analytics:read', 'View KPIs and reports'),
    (gen_random_uuid(), 'ai:use', 'Use AI-assisted features'),
    (gen_random_uuid(), 'audit:read', 'View the audit trail');
