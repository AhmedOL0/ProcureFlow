# ProcureFlow monitoring (Phase 1: configs only, no services yet).
#
# Contents:
# - prometheus/prometheus.yml — scrape topology (backend actuator + self).
# - grafana/provisioning/ — datasource + dashboard providers (file-based).
# - grafana/dashboards/http-overview.json — starter dashboard (HTTP rate, JVM heap).
#
# Wiring (Phase 2+): add `prometheus` and `grafana` services to the root
# docker-compose.yml with these files mounted, then open Grafana and import.
# Planned metric families: HTTP latency/errors, DB pool, JVM, Redis hit
# rate, domain counters (purchase requests, approvals), AI calls/latency/
# errors/token usage. Alert rules (`alerts.yml`) arrive with the first SLOs.
