-- ============================================================
-- V1: core identity + organization schema.
-- Tenants root all isolation; users belong to exactly one tenant;
-- roles bundle permissions per tenant. Later migrations (V2+)
-- add suppliers, procurement, approvals, budgets, orders,
-- invoices and audit. Migrations are append-only: never edit
-- a shipped migration, always add a new one.
-- ============================================================

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ---------- tenants ----------
CREATE TABLE tenants (
    id         UUID        PRIMARY KEY,
    name       VARCHAR(200) NOT NULL,
    slug       VARCHAR(100) NOT NULL UNIQUE,
    status     VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- ---------- users ----------
CREATE TABLE users (
    id            UUID         PRIMARY KEY,
    tenant_id     UUID         NOT NULL REFERENCES tenants (id),
    email         VARCHAR(320) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    first_name    VARCHAR(100),
    last_name     VARCHAR(100),
    status        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_users_tenant_email UNIQUE (tenant_id, email)
);
CREATE INDEX ix_users_tenant_id ON users (tenant_id);

-- ---------- roles ----------
CREATE TABLE roles (
    id          UUID         PRIMARY KEY,
    tenant_id   UUID         NOT NULL REFERENCES tenants (id),
    name        VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_roles_tenant_name UNIQUE (tenant_id, name)
);
CREATE INDEX ix_roles_tenant_id ON roles (tenant_id);

-- ---------- permissions (global codes, bound per tenant via roles) ----------
CREATE TABLE permissions (
    id          UUID         PRIMARY KEY,
    code        VARCHAR(150) NOT NULL UNIQUE,
    description VARCHAR(500),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- ---------- user <-> role ----------
CREATE TABLE user_roles (
    user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role_id UUID NOT NULL REFERENCES roles (id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, role_id)
);

-- ---------- role <-> permission ----------
CREATE TABLE role_permissions (
    role_id       UUID NOT NULL REFERENCES roles (id) ON DELETE CASCADE,
    permission_id UUID NOT NULL REFERENCES permissions (id) ON DELETE CASCADE,
    PRIMARY KEY (role_id, permission_id)
);

-- ---------- departments (optional tree inside a tenant) ----------
CREATE TABLE departments (
    id         UUID         PRIMARY KEY,
    tenant_id  UUID         NOT NULL REFERENCES tenants (id),
    name       VARCHAR(200) NOT NULL,
    parent_id  UUID         REFERENCES departments (id),
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_departments_tenant_name UNIQUE (tenant_id, name)
);
CREATE INDEX ix_departments_tenant_id ON departments (tenant_id);

-- ---------- user <-> department ----------
CREATE TABLE memberships (
    id            UUID        PRIMARY KEY,
    user_id       UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    department_id UUID        NOT NULL REFERENCES departments (id) ON DELETE CASCADE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_memberships_user_department UNIQUE (user_id, department_id)
);
