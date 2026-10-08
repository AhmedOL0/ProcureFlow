# ProcureFlow — AI Workflow Rules

Rules for AI coding agents on this repository. Commands, not suggestions.

## Approach

- Work incrementally with the context files as the system of record:
  `project-overview.md` (what), `architecture.md` (how it fits),
  `code-standards.md` + `ui-context.md` (rules), `progress-tracker.md`
  (state). Implement against these files; resolve ambiguity in the right
  file before coding, never by guessing.

## Git workflow (best practices, enforced)

- Branching: `main` is always green. All work happens on
  `feature/<epic>` branches (one per epic, created upfront and pushed).
  Never commit directly to `main`.
- One branch = one epic slice; land via PR with: green CI, reviewed diff,
  synced docs, screenshot/trace for UI changes. No force-push to shared
  branches, ever. Squash or keep logical commits per the epic, then merge.
- Commits: Conventional Commits (`feat(auth): ...`, `test: ...`,
  `docs: ...`, `chore: ...`). Small, logical, buildable at every commit.
- Push only after the local gates pass (see Verification).

## Scoping rules

- One feature unit at a time; one active task at a time.
- Small verifiable increments over speculative bulk. Never combine
  unrelated boundaries (UI + DB + messaging) in one step.
- Split a step that mixes UI with API changes, multiple concerns, or
  behavior undefined in the context files.

## Missing requirements

- Do not invent product behavior. Ambiguity → resolve in the context
  file or ask the user. Missing → record as an open question in
  `progress-tracker.md` before continuing.

## Protected paths (explicit instruction required to touch)

- `backend/src/main/resources/db/migration/*` numbering/order
  (renumbering breaks deployed databases).
- `.env` (must never exist in git), any file containing a real secret.
- `.github/workflows/*` gates (strengthen, never silently weaken).
- `docker-compose.yml` service contract and required-var (`:?`) guards.

## Docs sync (same change, no exceptions)

- Boundaries/architecture → `architecture.md` (+ `docs/` detail).
- Conventions → `code-standards.md` / `ui-context.md`.
- Scope → `project-overview.md`.
- Phases, done units, decisions, open questions → `progress-tracker.md`.

## Verification — the local CI gate

**NEVER push before the gates pass locally.** The GitHub run must be a
formality. Before every push:

1. `cd backend && ./mvnw.cmd -B verify` (unit + Testcontainers
   integration + ArchUnit + JaCoCo; needs Docker running).
2. `cd frontend && npm run build && npm test && npm run test:e2e`
   (install Playwright chromium once: `npx playwright install chromium`).
3. `docker compose config --quiet` after any compose edit.
4. Secrets scan: no `sk-`/`AKIA`/private-key patterns, no `.env` file.
5. Only then: commit (logical units) and push. Failing gates block the
   push — fix forward, never push red.

## Before moving to the next unit

1. Works end to end within its defined scope; no `architecture.md`
   invariant violated.
2. `progress-tracker.md` updated; docs synced.
3. All gates above green. Then stop and wait for user validation.
