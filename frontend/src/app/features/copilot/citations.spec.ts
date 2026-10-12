import { describe, expect, it } from 'vitest';

import { citationRoute } from './citations';

describe('citationRoute', () => {
  it('links analytics citations to the analytics page', () => {
    expect(citationRoute('requestedMinor=1 via GET /analytics/spend')).toBe('/analytics');
    expect(citationRoute('GET /analytics/spend?period=2026-10')).toBe('/analytics');
    expect(citationRoute('GET /analytics/approvals')).toBe('/analytics');
  });

  it('leaves unknown shapes as plain text', () => {
    expect(citationRoute('3 quotations compared')).toBeNull();
    expect(citationRoute(null)).toBeNull();
    expect(citationRoute('')).toBeNull();
  });
});
