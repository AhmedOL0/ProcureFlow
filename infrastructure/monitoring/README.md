# ProcureFlow monitoring (live, opt-in profile)

Prometheus scrapes the backend actuator plus itself; alert rules
(`prometheus/alerts.yml`: overspend spike, 5xx rate, AI failing/usage)
are loaded and evaluated. Grafana serves both datasources (Prometheus +
Loki) with the starter dashboard provisioned. Promtail ships every
`procureflow-*` container log to Loki with `level` + `trace` labels
parsed from the correlation format.

Contents:

- `prometheus/prometheus.yml` — scrape topology (backend actuator + self)
- `prometheus/alerts.yml` — starting-point thresholds; tune from history
- `loki/loki.yml` — single-node filesystem Loki (local only)
- `promtail/promtail.yml` — docker discovery + level/trace parsing
- `grafana/provisioning/` — Prometheus + Loki datasources, dashboard provider
- `grafana/dashboards/http-overview.json` — starter dashboard (HTTP rate, JVM heap)

## Run

```bash
docker compose --profile observability up -d prometheus grafana loki promtail
# Prometheus http://localhost:9090  (/-/healthy, /targets, /rules)
# Grafana    http://localhost:3000  (admin / $GRAFANA_ADMIN_PASSWORD)
# Loki       http://localhost:3100  (/ready)
```

Requires `GRAFANA_ADMIN_PASSWORD` in `.env` (see `.env.example`).
Default stack is unaffected — the profile adds containers only.

## Verify

```bash
curl -s http://localhost:9090/-/healthy
curl -s http://localhost:9090/api/v1/targets | grep -o '"health":"up"' | sort | uniq -c
curl -s http://localhost:9090/api/v1/rules | grep -o '"name":"[A-Za-z]*"' | sort -u
curl -s http://localhost:3100/ready
# Loki has backend lines (range query — instant queries miss outside
# their default window; labels prove ingestion either way):
curl -s -G http://localhost:3100/loki/api/v1/query_range --data-urlencode \
  'query={container="procureflow-backend",level="INFO"}' \
  --data-urlencode 'limit=2' | grep -o 'trace=[^ "]*' | head -3
curl -s http://localhost:3100/loki/api/v1/label/container/values
# Grafana datasources provisioned (admin auth):
curl -s -u admin:$GRAFANA_ADMIN_PASSWORD http://localhost:3000/api/datasources | grep -o '"name":"[A-Za-z]*"'
```

Planned metric families: HTTP latency/errors, DB pool, JVM, domain
counters (requests, approvals, budgets, invoices, AI calls/tokens).
Receivers and SLOs arrive with a production target — until then the rules
evaluate locally with nowhere to page, which is stated, not hidden.
