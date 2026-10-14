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

Aggregate only (per-endpoint tags deferred until a bottleneck shows).
Seed data is small (demo-scale), so this is a floor ruler: it proves the
read mix is healthy locally, not that all-time spend scales. The H-3
residual (all-time grouping depth) is still the first suspect at scale.

## Debris

Each run leaves one `load-*` tenant + user + a handful of refresh/audit
rows in the dev volume — labeled, bounded, never in `demo`. To drop them:

```sql
-- same dependency order as docs/demo.md reset, scoped to load tenants:
DELETE FROM users WHERE tenant_id IN (SELECT id FROM tenants WHERE slug LIKE 'load-%');
DELETE FROM tenants WHERE slug LIKE 'load-%';
```
