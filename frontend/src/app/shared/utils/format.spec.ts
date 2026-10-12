import { describe, expect, it } from 'vitest';
import { formatDate, formatMoney, truncate } from './format';

describe('formatMoney', () => {
  it('formats minor units as major currency', () => {
    expect(formatMoney(2000)).toContain('20.00');
  });

  it('handles zero', () => {
    expect(formatMoney(0)).toContain('0.00');
  });
});

describe('truncate', () => {
  it('leaves short text untouched', () => {
    expect(truncate('short')).toBe('short');
  });

  it('truncates long text with an ellipsis', () => {
    const out = truncate('a'.repeat(100), 80);
    expect(out.length).toBeLessThanOrEqual(80);
    expect(out.endsWith('…')).toBe(true);
  });
});

describe('formatDate', () => {
  it('formats an ISO timestamp as a short date', () => {
    expect(formatDate('2026-10-12T10:00:00Z')).toBe('12 Oct 2026');
  });

  it('returns a dash for missing or invalid input', () => {
    expect(formatDate(null)).toBe('—');
    expect(formatDate(undefined)).toBe('—');
    expect(formatDate('not-a-date')).toBe('—');
  });
});
