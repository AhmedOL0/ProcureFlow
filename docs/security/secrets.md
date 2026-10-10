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

## Outbound mail (Brevo SMTP)

7. Provider setup (Brevo): transactional SMTP relay at
   `smtp-relay.brevo.com:587` with STARTTLS. `MAIL_USERNAME` is the Brevo
   account login email; `MAIL_PASSWORD` is a Brevo SMTP key (`xsmtpsib-…`,
   generated under SMTP & API → SMTP keys — it is NOT the account
   password and NOT a `v3` API key). `MAIL_FROM` must be a
   Brevo-validated sender address or delivery fails at submission.
8. `MAIL_PROVIDER=smtp` selects the Brevo adapter; anything else (default
   `log`) sinks mail to a safe log line (recipient + subject only — never
   tokens or URLs). Timeouts are 5s connect / 10s read/write.
9. `APP_FRONTEND_URL` must be the absolute browser origin
   (e.g. `https://app.example.com`); reset links are built from it and the
   backend refuses to mint them from a non-http(s) value.
10. Local development needs no SMTP: leave `MAIL_PROVIDER=log` and exercise
    the flow in tests with a mocked `MailPort`. For a real local delivery
    test, set the four `MAIL_*` vars plus `MAIL_PROVIDER=smtp` in the
    git-ignored `.env` and watch the inbox — never commit those values.
