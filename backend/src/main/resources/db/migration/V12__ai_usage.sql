-- ============================================================
-- V12: AI usage metering (Phase 6, epic 11 slice 1).
-- One row per provider call: who spent what on which feature with which
-- prompt version. Read by the usage endpoint; never updated or deleted
-- through the API.
-- ============================================================

CREATE TABLE ai_usage (
    id                UUID        PRIMARY KEY,
    tenant_id         UUID        NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    feature           VARCHAR(40) NOT NULL,
    model             VARCHAR(100) NOT NULL,
    prompt_version    VARCHAR(20) NOT NULL,
    prompt_tokens     INTEGER     NOT NULL DEFAULT 0,
    completion_tokens INTEGER     NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_ai_usage_tenant_id ON ai_usage (tenant_id, created_at DESC);
