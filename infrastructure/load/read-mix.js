import http from 'k6/http';
import { check, sleep } from 'k6';

// First load measurement: read-heavy mix against the local compose backend.
// One workspace is registered in setup() (labeled load-*, documented debris,
// see README); VUs only read afterwards. Login/refresh throttles are never
// touched by the loop (single setup login, token reused).
//
// Run from the repo root:
//   docker run --rm -i -v "%CD%/infrastructure/load:/scripts" grafana/k6:0.57.0 \
//     run --vus 5 --duration 2m /scripts/read-mix.js
// (PowerShell: ${PWD}/infrastructure/load). Backend must be up on :8081.

const BASE = __ENV.BASE_URL || 'http://host.docker.internal:8081';

export const options = {
  thresholds: {
    // Generous first-pass gates; the recorded numbers, not the gates,
    // are the deliverable. Tighten only from history.
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<2000'],
  },
};

export function setup() {
  const stamp = Date.now();
  const slug = `load-${stamp}`;
  const email = `load@${slug}.test`;
  const reg = http.post(
    `${BASE}/api/v1/auth/register`,
    JSON.stringify({
      email,
      password: 'Load-Probe-2026!',
      tenantSlug: slug,
      tenantName: `Load ${stamp}`,
    }),
    { headers: { 'Content-Type': 'application/json' } },
  );
  check(reg, { 'register 201': (r) => r.status === 201 });

  const login = http.post(
    `${BASE}/api/v1/auth/login`,
    JSON.stringify({ email, password: 'Load-Probe-2026!' }),
    { headers: { 'Content-Type': 'application/json' } },
  );
  check(login, { 'login 200': (r) => r.status === 200 });
  return { token: login.json('accessToken'), slug };
}

const auth = (token) => ({ headers: { Authorization: `Bearer ${token}` } });

export default function (data) {
  // Liveness baseline (infra, no DB).
  let r = http.get(`${BASE}/actuator/health/liveness`);
  check(r, { 'liveness 200': (x) => x.status === 200 });

  // H-3 path: all-time spend aggregation over full tenant history.
  r = http.get(`${BASE}/api/v1/analytics/spend`, auth(data.token));
  check(r, { 'spend all-time 200': (x) => x.status === 200 });

  // Bounded sibling: single-month spend (period pushdown path).
  r = http.get(`${BASE}/api/v1/analytics/spend?period=2026-10`, auth(data.token));
  check(r, { 'spend period 200': (x) => x.status === 200 });

  // Paged directory read.
  r = http.get(`${BASE}/api/v1/suppliers?page=0&size=20`, auth(data.token));
  check(r, { 'suppliers 200': (x) => x.status === 200 });

  sleep(1);
}
