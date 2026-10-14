# Production shape (LOCAL ONLY — drilled here, never applied)

Reference topology for ADR-011: TLS edge → SPA/API, Postgres with WAL
archiving, enforced antivirus, encrypted dumps, observability. It runs
entirely on this machine against **separate volumes** (`pgdata-prod`,
`pgwal`, `pgbackups-prod`, `tlsdata`) — the dev stack is never touched.

## Bring-up

```powershell
$env:BACKUP_ENCRYPT_KEY = 'a-long-random-passphrase-kept-out-of-git'
$env:EDGE_HOST = 'localhost'   # browser name for emailed links + baked API base
docker compose -f docker-compose.yml -f docker-compose.prod.yml `
  --profile scan --profile backup --profile observability up -d --build
# App: https://localhost:8443 (self-signed on first boot: browser warning expected)
# API:  https://localhost:8443/api/v1/... (same origin)
# /actuator is NOT proxied: metrics stay container-network-only for Prometheus.
```

Required: `POSTGRES_PASSWORD`, `JWT_SECRET` (≥64 chars, app refuses less),
`BACKUP_ENCRYPT_KEY`. Everything sensitive travels by env/file, never in git.

Learned drilling (read before operating):

- Dev and prod observability services share container names: `rm` the idle
  dev observers (`docker rm -f procureflow-{loki,prometheus,grafana,promtail}`)
  before prod up, or the name conflict aborts the boot.
- Replication access is declarative: `pg-init-hba.sh` (initdb.d hook)
  appends the scram replication line on first init — no manual pg_hba step.
- WAL archives bind to a database incarnation (system identifier): wiping
  `pgdata-prod` without wiping `pgwal` poisons replay with same-named
  segments from the dead incarnation (`different database system` FATAL).
  Reset both together, always.
- `pg_basebackup -Ft` writes `base.tar.gz` AND `pg_wal.tar.gz`: extract
  both, or recovery cannot find its checkpoint.
- Every `docker compose` invocation against this file needs all three
  `-f` + `--profile` flags, including one-off `exec` (strict depends_on
  validation); `ports: !reset []` keeps DB ports unpublished.

## What differs from dev (and why)

- **TLS edge** (`edge`, nginx): :443 with HSTS-ready headers. First boot
  mints a throwaway self-signed pair into `tlsdata`; for anything shared,
  drop ACME-issued `edge.crt`/`edge.key` into the volume and restart —
  no rebuild. HSTS stays commented until a real name serves.
- **Postgres**: WAL archiving to `pgwal` (`wal_level=replica`,
  `archive_command` to /wal), no published ports, resource-capped.
- **Backend**: `prod` Spring profile (forwarded-header strategy behind
  the proxy), `CLAMAV_HOST=clamav` enforced, no published ports,
  `mem_limit 1g / cpus 1.0`.
- **Frontend**: rebuilt with `ANGULAR_API_URL=https://<edge>` (same-origin
  through the edge), no published ports.
- **Backups**: encrypted dumps mandatory (`BACKUP_ENCRYPT_KEY` fail-fast);
  decrypt with
  `openssl enc -d -aes-256-cbc -pbkdf2 -pass env:BACKUP_ENCRYPT_KEY
   -in <file>.enc | pg_restore -d <db>`.
- **Observability/malware profiles** ride along (Prometheus/Grafana/Loki,
  ClamAV) — in real prod Grafana/Prometheus bind to localhost/VPN, not
  the world; the published ports here are a local-drill convenience.

## RPO / RTO (measured locally, 2026-10-14)

- **RPO**: WAL segments archive within seconds of commit; the PITR drill
  replayed to latest with **zero committed rows lost** (pre-base canary
  and post-base marker both back). Worst case = base age + unarchived
  tail, both visible (`/backups` timestamps, `pg_stat_archiver`).
- **RTO**: destroy 02:08:04 → rows verified 02:09:44 ≈ 100 s wall, manual
  pacing included; the replay itself took ~1 s (redo + timeline switch).
  Inside the ADR-011 objectives (RPO ≤ 24h, RTO ≤ 4h) with two orders of
  magnitude to spare. Re-measure on real hardware before promising it.

## Restore drills (run these, in order)

```powershell
$env:BACKUP_ENCRYPT_KEY = 'drill-only-key'
# 1. Encrypted dump + restore:
docker compose -f docker-compose.yml -f docker-compose.prod.yml --profile backup run --rm db-backup sh /backup.sh once
# -> /backups/procureflow-<ts>.dump.enc in pgbackups-prod; decrypt + pg_restore into a scratch DB, verify, drop.
# 2. PITR: canary row -> pitr-base.sh -> more writes -> destroy pgdata-prod
#    -> extract base -> recovery.signal + restore_command -> start ->
#    verify canary AND post-base writes -> drop scratch.
```

Evidence for both drills lives below (dates, files, row counts).
Never restore over live data: scratch first, promote by rename.

## Drill evidence

### Encrypted dump + restore (2026-10-14, local)

drill-only key `drill-only-key` (never reused, never committed).

- Dump: `pgbackups-prod:/backups/procureflow-20261014-014329.dump.enc`
  (93,504 bytes, AES-256-CBC PBKDF2; `ls /backups` shows only
  `*.dump.enc` — no plaintext `.dump` survives the run).
- Restore: decrypted stream piped to `pg_restore -d procureflow_restore`;
  1 tenant + 2 suppliers (`Drill Supplier`, `Second Drill Supplier`)
  selected back exactly; scratch dropped.

### PITR base + WAL replay (2026-10-14, local)

- Canary `pitr_drill_canary` row 1 written pre-base; base via
  `pitr-base.sh` → `base-20261014-020721` (4.3 MB + 17 KB pg_wal tar);
  marker row 2 written post-base; `pgdata-prod` destroyed; base +
  pg_wal tars extracted; `recovery.signal` + `restore_command` replayed
  to latest on timeline 2; **canary AND marker both selected back**;
  scratch dropped. Archive held 5 segments at restore time.
- Backend restarted healthy on the recovered DB; registration through
  the TLS edge answered 201 (full chain on recovered data).

## What this does NOT claim

No cloud account, no real certificates, no off-host copies (the "off-site"
leg is `BACKUP_ENCRYPT_KEY` + a documented `rclone sync pgbackups-prod`
away — the sync destination does not exist here), no load numbers on this
topology, no 24/7 operation. Managed Postgres stays the recommendation
the day shared tenant data exists (ADR-011).
