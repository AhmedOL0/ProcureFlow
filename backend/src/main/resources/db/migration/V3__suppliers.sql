-- ============================================================
-- V3: supplier management schema.
-- Suppliers belong to one tenant; contacts and performances die with
-- their supplier (ON DELETE CASCADE); category links are pure join rows.
-- Supplier deletion while referenced by purchase orders is refused in
-- code once those tables exist (Phase 5); until then deletes are direct.
-- ============================================================

CREATE TABLE suppliers (
    id         UUID         PRIMARY KEY,
    tenant_id  UUID         NOT NULL REFERENCES tenants (id),
    name       VARCHAR(200) NOT NULL,
    tax_id     VARCHAR(100),
    email      VARCHAR(320),
    phone      VARCHAR(50),
    address    VARCHAR(500),
    status     VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_suppliers_tenant_name UNIQUE (tenant_id, name)
);
CREATE INDEX ix_suppliers_tenant_id ON suppliers (tenant_id);

CREATE TABLE supplier_contacts (
    id          UUID         PRIMARY KEY,
    supplier_id UUID         NOT NULL REFERENCES suppliers (id) ON DELETE CASCADE,
    name        VARCHAR(200) NOT NULL,
    email       VARCHAR(320),
    phone       VARCHAR(50),
    title       VARCHAR(100),
    is_primary  BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_supplier_contacts_supplier_id ON supplier_contacts (supplier_id);

CREATE TABLE supplier_categories (
    id          UUID         PRIMARY KEY,
    tenant_id   UUID         NOT NULL REFERENCES tenants (id),
    name        VARCHAR(200) NOT NULL,
    description VARCHAR(500),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_supplier_categories_tenant_name UNIQUE (tenant_id, name)
);
CREATE INDEX ix_supplier_categories_tenant_id ON supplier_categories (tenant_id);

CREATE TABLE supplier_category_links (
    supplier_id UUID NOT NULL REFERENCES suppliers (id) ON DELETE CASCADE,
    category_id UUID NOT NULL REFERENCES supplier_categories (id) ON DELETE CASCADE,
    PRIMARY KEY (supplier_id, category_id)
);

CREATE TABLE supplier_performances (
    id             UUID          PRIMARY KEY,
    supplier_id    UUID          NOT NULL REFERENCES suppliers (id) ON DELETE CASCADE,
    period         VARCHAR(7)    NOT NULL,
    on_time_rate   NUMERIC(5, 2),
    quality_score  NUMERIC(5, 2),
    notes          TEXT,
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_supplier_performances_supplier_period UNIQUE (supplier_id, period)
);
CREATE INDEX ix_supplier_performances_supplier_id ON supplier_performances (supplier_id);
