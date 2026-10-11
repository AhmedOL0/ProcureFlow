-- ============================================================
-- V19: single-primary contact guard at the database level. The
-- application unsets siblings first, but two concurrent writers could
-- both commit primary=true; this partial unique index makes the loser
-- fail instead of silently doubling the primary.
-- ============================================================

CREATE UNIQUE INDEX uq_supplier_contacts_supplier_primary
    ON supplier_contacts (supplier_id) WHERE is_primary;
