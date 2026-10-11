-- ============================================================
-- V16: retire the password-reset throttle ledger. Auth and reset flows
-- share one policy and one ledger now (AuthAttemptThrottle over the
-- auth_throttle table, V14); reset buckets are namespaced inside it.
-- Throttle rows are ephemeral counts, so dropping the table loses no
-- business state.
-- ============================================================

DROP TABLE password_reset_throttle;
