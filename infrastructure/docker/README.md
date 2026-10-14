# Local Docker usage: `docker compose up -d postgres` for
# development dependencies, or `docker compose up -d --build` for the full
# stack once backend/frontend images are built. Required secrets come from
# `.env` (see root `.env.example`); compose fails fast if they are missing.
# Service catalog: postgres:5432,
# backend:8080, frontend:4200 (nginx as the unprivileged nginx user on
# in-container :8080).

## Backup & restore (postgres)

An opt-in `db-backup` sidecar (compose profile `backup`, postgres:16-alpine
so `pg_dump` always matches the server version) writes one compressed
custom-format dump per run into the `pgbackups` volume and prunes to
`BACKUP_RETENTION` newest dumps (default 7). It never runs with the default
stack — request it explicitly:

```powershell
# One dump right now:
docker compose --profile backup run --rm db-backup sh /backup.sh once
# Nightly sidecar (dumps every BACKUP_INTERVAL_SECONDS, default 86400):
docker compose --profile backup up -d db-backup
# What do we hold?
docker compose --profile backup run --rm db-backup ls -t /backups
```

Restore into a scratch database first — never straight over live data:

```powershell
docker compose exec postgres psql -U procureflow -d postgres `
  -c 'CREATE DATABASE procureflow_restore;'
docker compose --profile backup run --rm db-backup sh -c `
  'pg_restore -d procureflow_restore /backups/<dump-file>'
# verify, then either promote (rename) or drop the scratch database
docker compose exec postgres psql -U procureflow -d postgres `
  -c 'DROP DATABASE procureflow_restore;'
```

Drill evidence (2026-10-11, local dev volume): one-shot dump
`procureflow-20261011-101620.dump` (175901 bytes); canary row
`backup_drill_canary(1, 'drill-2026-10-11')` restored into
`procureflow_restore` and selected back intact; scratch database and
canary dropped afterwards. Retention pruning and the loop re-arm were
read from the script, not exercised — re-drill before relying on them
for anything real. This covers local development only: production needs
managed backups (point-in-time recovery), encryption at rest, and a
tested restore SLO, none of which this sidecar claims.
