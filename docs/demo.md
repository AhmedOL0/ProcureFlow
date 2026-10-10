# Demo walkthrough (5 minutes, synthetic data only)

The `demo` workspace ships with one user per role plus a supplier, a
submitted request, a budget pot, an approval lane and a department — enough
that no screen is empty. Everything here is fictional. **Password for all
six accounts: `ProcureFlow-2026!`** (demo-only; never reuse in production).

```bash
docker compose up -d --build   # backend :8081, frontend :4200
# open http://localhost:4200
```

| Minute | As | Do |
|---|---|---|
| 1 | `admin@demo.test` | Dashboard → Suppliers → Requests; note the full rail |
| 2 | `approver@demo.test` | Approvals inbox → open *Demo workstation fleet* → Approve with a comment |
| 3 | `admin@demo.test` | Orders → create from the approved request → Send; Invoices → book → pay |
| 4 | `finance@demo.test` | Budgets & Spend → New pot; Analytics → spend trajectory |
| 5 | `auditor@demo.test` | Audit Logs → expand a row (before/after payloads); try any write (honest 403) |

Extras: `member@demo.test` sees 9 rail entries and a 403 behind `/budgets`;
`admin@demo.test` → ProcureAI Copilot (needs `GROQ_API_KEY` + `AI_ENABLED=true`
in `.env`, otherwise an honest disabled message); Users & Roles creates
accounts with any of the six roles.

## Reset procedure

Demo data lives in the shared `pgdata` volume alongside everything else.
To reset the demo workspace without touching other tenants, delete and
re-register it:

```bash
# inside the backend container's database (dev only):
# DELETE FROM tenants WHERE slug = 'demo';  # cascades workspace rows
```

then re-register and re-seed through the API (replace `$T` with the admin
bearer token):

```bash
BASE=http://localhost:8081/api/v1
# register admin@demo.test (tenant demo), log in, then:
curl -X POST $BASE/suppliers -H "Authorization: Bearer $T" \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: demo-sup' \
  -d '{"name":"Bechtle Demo AG","status":"ACTIVE"}'
# create a request (note its id from the response as REQ):
curl -X POST $BASE/purchase-requests -H "Authorization: Bearer $T" \
  -H 'Content-Type: application/json' -H 'Idempotency-Key: demo-req' \
  -d '{"title":"Demo workstation fleet","priority":"HIGH"}'
curl -X POST $BASE/purchase-requests/$REQ/items -H "Authorization: Bearer $T" \
  -H 'Content-Type: application/json' \
  -d '{"description":"Laptop 16in","quantity":15,"unitPriceMinor":120000}'
curl -X POST $BASE/purchase-requests/$REQ/submit -H "Authorization: Bearer $T"
curl -X POST $BASE/budgets -H "Authorization: Bearer $T" \
  -H 'Content-Type: application/json' \
  -d '{"name":"Engineering","period":"2026-10","amountMinor":5000000}'
```

Never point a reset at a non-demo tenant.
