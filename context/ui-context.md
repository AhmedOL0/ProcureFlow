# ProcureFlow — UI Context

## Stack and conventions

- Angular 21, TypeScript strict, Signals for local state, RxJS for streams,
  Reactive Forms for all input. No `any` in shipped code.
- Feature modules behind lazy routes (`app.routes.ts` holds the commented
  pattern); `core/` (auth, http, guards, interceptors, config),
  `shared/` (components, directives, pipes, utils), `features/*`, `layout/`.
- API base from `src/environments/*` only; auth token attached by an
  interceptor (Phase: `feature/frontend-auth`); generated API types from
  OpenAPI once Phase 3 codegen lands — no hand-duplicated DTOs after that.
- Component library choice (Angular Material vs bespoke) is an open
  question in `progress-tracker.md`; do NOT install a library until it is
  decided and recorded. No UI dependency for decoration alone.

## Anti-slop design rules (mandatory)

Vibe-coded design is rejected in review. Every screen must pass these:

1. **No generic AI aesthetic.** No purple-blue gradients, no glassmorphism
   soup, no floating blobs, no identical rounded-card grids with emoji
   icons. If the design could belong to any SaaS, it belongs to none —
   redesign it with procurement-specific content and hierarchy.
2. **Real content, real states.** No lorem ipsum, no hardcoded demo rows
   pretending to be data. Every list handles loading, empty, error and
   populated states explicitly. Empty states explain the next action.
3. **Typography with intent.** One UI font + one display font maximum;
   establish a type scale (display/title/body/caption) and reuse it — no
   per-component font-size improvisation.
4. **Color with a system.** Define tokens (surface, primary, accent,
   success, warning, danger, muted text) before styling components; no raw
   hex scattered in templates. Muted text must keep ≥4.5:1 contrast.
5. **Density fits the job.** Procurement is a data-dense back office:
   tables with sorting/filtering beat card grids for operational screens.
   Whitespace serves scanning, not decoration.
6. **Motion is functional.** Transitions explain state changes (drawer,
   dialog, route); nothing bounces, floats or auto-plays. Respect
   `prefers-reduced-motion`.
7. **Accessible by default.** Labels on every input, focus visible,
   keyboard-reachable actions, semantic landmarks, axe-clean before merge.
8. **Responsive from the start.** Desktop-first for back-office tables,
   but nothing may break at 360px; horizontal scroll is a table's right,
   not a layout failure.

## Review gate

A UI change merges only with: a screenshot or Playwright trace in the PR,
all four data states covered, no new accessibility violations, and no new
hardcoded colors or font sizes. When in doubt, simpler ships.
