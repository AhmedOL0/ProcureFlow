# ADR-011: Production topology (reference implemented locally)

- Status: Reference implemented 2026-10-14 (`docker-compose.prod.yml` +
  `infrastructure/prod/`); managed-Postgres path still recommended for
  shared data. Nothing applied to real infrastructure.

- Status: Reference implemented (see 2026-10-14 update at the end); the managed-DB recommendation below still stands for shared data.
- Context: ProcureFlow runs local/demo only today — compose with a single
  `pgdata` volume, a local-only backup sidecar (dump + one canary restore
  drilled), empty Terraform, no TLS/proxy, secrets in an env file, no
  resource limits, no rollback procedure, RPO/RTO undefined. Audit
  readiness stays "Functional Application" until this lands. What already
  exists and carries over: multi-stage images (backend + frontend non-root),
  container healthchecks (liveness probe, public), Flyway validate-everywhere,
  versioned migrations, correlation IDs, contractual error envelopes.
- Decision (recommended): managed Postgres plus a small PaaS/VPS footprint.
  - Database: managed Postgres with PITR (Neon, Supabase, RDS, Cloud SQL —
    pick on cost/data-residency). PITR plus daily encrypted off-site dumps
    gives a real RPO story the local sidecar cannot.
  - App: the existing images unchanged; TLS terminator in front; secrets
    into the platform manager (no env files on hosts); tagged releases
    (`:latest` never deployed — rollback = previous tag + `flyway repair`
    discipline documented in the runbook).
  - Proposed objectives for sign-off: **RPO ≤ 24h, RTO ≤ 4h**, proven by a
    restore drill against a scratch instance before the first shared tenant.
  - Alternative considered (self-hosted compose extended): keep the topology,
    extend the sidecar (encrypted off-site copies, exercised retention loop +
    restore drill), add TLS + secret handling. Cheaper, more ops burden.
  - Alternative considered (defer): stay local/demo only. Valid, but then
    every readiness claim stays scoped and H-2 stays open indefinitely.
- Consequences: a cloud bill and an ops owner; the backup sidecar becomes
  the local-dev story, not the durability story. Monitoring configs
  (`infrastructure/monitoring`) finally get a collector to aim at, which
  also retires the observability gap for the production topology.
- 2026-10-14 update: the self-hosted alternative is now BUILT and DRILLED
  locally as the deployment-ready reference (TLS edge with snakeoil boot,
  WAL archiving + PITR replay proven, encrypted dumps + restore proven,
  enforced antivirus, RPO zero-loss / RTO ~100 s measured, Hetzner target
  in `infrastructure/terraform` validated but never applied). What stays
  open for shared data: a real account, real certificates, off-host copies,
  managed Postgres with PITR. Revisit triggers unchanged.
- Revisit triggers: signing the first shared/demo-external tenant (must
  resolve before), or cost/residency forcing a different target (rewrite
  this ADR with the new topology).
