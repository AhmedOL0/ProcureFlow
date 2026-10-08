# Secrets handling

1. Every credential enters through environment variables (see `.env.example`
   for the full list). No secret is ever hardcoded, including in tests.
2. Local development uses a git-ignored `.env` file. Production uses the
   deployment platform's secret store; CI uses GitHub repository secrets.
3. `JWT_SECRET` must be at least 64 random characters (HS512). The Phase 2
   startup validation refuses to boot on placeholder values.
4. `GROQ_API_KEY` lives server-side only. It must never appear in Angular
   code, frontend build output, URLs, or logs. If it leaks, rotate it and
   treat the incident as a security event.
5. Database and broker passwords are generated per environment, never reused
   across prod and non-prod.
6. `security.yml` runs Trivy filesystem scans and `npm audit` on every PR
   as a tripwire; it complements, never replaces, careful handling.
