# Load probes (first measurement, local only)

k6 read-heavy mix against the compose backend on `:8081`. This is a first
ruler against the floor, not a capacity claim: one laptop, one run, small
data. Re-run after any H-3/perf work and compare with the table below.

## Run

Backend must be up (`docker compose up -d backend`):

```powershell
docker run --rm -i -v "${PWD}/infrastructure/load:/scripts" grafana/k6:0.57.0 `
  run --vus 5 --duration 2m /scripts/read-mix.js
```

`setup()` registers one `load-<millis>` workspace and logs in once; VUs
then only read (liveness, all-time spend, monthly spend, supplier page).
Login/refresh throttles are never exercised by the loop by design.

## Results

| Date | Mix | VUs / duration | p50 | p95 | p99 | Failed | Notes |
|---|---|---|---|---|---|---|---|
| 2026-10-14 | read-mix.js | 5 / 2m | 6.9 ms | 20.5 ms | — | 0 / 2322 | max 2.49 s single cold-start outlier; tiny seed data |
| 2026-10-14 | read-mix.js (`TOKEN`, scale tenant) | 5 / 2m | 9.5 ms | 25.8 ms | — | 0 / 2300 | max 95 ms; 10k requests + 20k lines via `seed-scale.sql` |

Aggregate only (per-endpoint tags deferred until a bottleneck shows).
Scale seed (`seed-scale.sql`, pure SQL): 8k APPROVED (2 h leads) + 2k
SUBMITTED spread over 365 days, 2 MAD lines each. Rows bypass the
application (no audit rows/events) — fine for read-load measurement.
p95 moved 20.5 → 25.8 ms for 1000x rows: the grouped aggregations hold
flat. Dropped after the run (same order as below).

## Debris

Each run leaves one `load-*` tenant + user + a handful of refresh/audit
rows in the dev volume — labeled, bounded, never in `demo`. To drop them:

```sql
-- same dependency order as docs/demo.md reset, scoped to load tenants:
DELETE FROM users WHERE tenant_id IN (SELECT id FROM tenants WHERE slug LIKE 'load-%');
DELETE FROM tenants WHERE slug LIKE 'load-%';
```
