/**
 * Citation deep-links: backend citations name the endpoint they came from
 * ("... via GET /analytics/spend"), so the UI can route there instead of
 * rendering dead text. Unknown shapes stay plain text — never guess a
 * route from model prose.
 */
export function citationRoute(cite: string | null | undefined): string | null {
  if (!cite) {
    return null;
  }
  if (cite.includes('/analytics/spend') || cite.includes('/analytics/approvals')) {
    return '/analytics';
  }
  return null;
}
