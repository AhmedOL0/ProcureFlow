# UX & frontend quality

Scope: `frontend/src/app`, `frontend/e2e`, Stitch reference at `stitch_procureflow_enterprise_saas_ui/` (24 design dirs, each with `code.html` + `screen.png`). Visual parity not rendered; comparison is structural (routes/components/contracts), verified by reading.

## Functional audit: real product, no Potemkin screens

Every route resolves to a working screen; every button navigates or submits to a typed service; 8 backend mappings spot-checked against UI calls — all match. Notable honesty points: columns with no backing API are omitted with code comments (not faked), notifications carry no deep links because the contract has none, and `markAllRead` fans out N PATCH calls with the missing bulk endpoint documented rather than simulated.

- States: loading/empty/error/403 on all directory, dossier, inbox, analytics, copilot, settings, and admin screens (verified per template).
- Forms: Reactive Forms + validators everywhere (57 `Validators.` hits); `markAllAsTouched` on invalid submit; destructive actions behind `ConfirmDialogComponent`; dirty-request drafts behind `pendingChangesGuard`.
- Money: integer minor units formatted centrally (`money.ts`, `format.ts` — both Vitest-covered); tabular numerals on figures.
- Copilot: per-mode loading/cancel/error including 503 `AI_DISABLED`; answers show citations + model; extract→review→confirm→save flow keeps the human decision.
- Gaps (functional): no 404 page (`**` redirects — P2/S); analytics category/supplier filters are client-side over full payloads (fine until pagination lands, then must move server-side); `markAllRead` N+1 and inbox per-row decision reads (P2/S bulk endpoint); no saved views/exports (P2/M, needs backend).

## Design-system audit

One font (self-hosted Inter), token system in `styles.scss` (Obsidian Emerald: spruce/viridian/copper/obsidian), no raw hex in components (grep-verified), shared `pf-*` primitives (page rhythm, KPI cards, table cards, filter consoles, pills, meters, dialogs), single Lucide catalog, density fit for back-office tables, functional motion only with `prefers-reduced-motion` support. Light rail (264px, overlay ≤1024px) verified by screenshot at 1440px and 390px this session cycle.

## Stitch comparison (sampled)

- Supplier directory → `supplier-list/`: match (KPI row, filter console + chips, dense table, dossier links). Documented divergence: spend/PO-volume/compliance columns omitted without APIs. Verified.
- Supplier dossier → `supplier-detail/`: match (identity header, contacts with primary rule, categories, scorecards). The reference comparison-matrix view is not implemented. Partially verified.
- Shell/dashboard/analytics/budgets/copilot/admin: navigation sections, KPI cards, trajectory bars, pot tables, mode-based copilot, and admin split all structurally present. Remaining divergences are documented scope cuts (bulk actions, SLA countdowns, tenant switcher, global search — no backing contracts).
- Typography/spacing: type scale and 4px spacing tokens hold; the one observed violation class (raw px gaps) was consolidated onto tokens.

## Accessibility

Present and real: skip link, `<main>` + nav landmarks, `mat-label` on all sampled inputs, `aria-label` on icon buttons, `aria-hidden` decoratives, visible `:focus-visible`, `cdkFocusInitial` on dialog actions, `role=status/alert/region` wiring. **Missing: automated axe checks in CI** (zero axe hits in specs/config) — markup without a gate regresses silently. P1/S: add axe-core to 5 key pages in `frontend-ci`.

## Code-health findings

- `valueChanges` subscriptions in ~12 routed components/dialogs are never torn down (no `takeUntilDestroyed`/`async` pipe anywhere). HttpClient one-shots self-complete; the long-lived form subscriptions re-subscribe per navigation. Medium leak risk under heavy navigation. P2/S.
- OnPush on 4 shared components only; ~25 feature components use default detection. No proven defect; note for render perf. P3.
- `api-types.gen.ts` contains all 34 called paths, but carries no generation proof — add a CI regenerate-and-diff job to catch drift. P2/S.
- Dead directories (`features/administration|ai|approvals|purchase-orders`, `.gitkeep` only) are unreferenced — harmless, but remove or document to avoid confusion. P3/S.
- No `console.log`/lorem/mock-data masquerading found. No 404-page. Bundle: ~1.3MB total JS / 6KB main across lazy chunks (measured); no perf budget in `angular.json`, no Lighthouse gate. P2/S for budgets.

## Browser matrix

Playwright runs Chromium only. Firefox/WebKit gaps are untested (the JWT base64url padding fix earlier suggests real cross-engine risk). P2/S: add Firefox to `frontend-ci` at minimum.
