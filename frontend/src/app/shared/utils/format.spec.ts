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

  it('truncates long text with trailing dots', () => {
    const out = truncate('a'.repeat(100), 80);
    expect(out.length).toBeLessThanOrEqual(80);
    expect(out.endsWith('...')).toBe(true);
  });
});

describe('formatDate', () => {
  it('formats an ISO timestamp as a short date', () => {
    expect(formatDate('2026-10-12T10:00:00Z')).toBe('12 Oct 2026');
  });

  it('formats with an explicit French locale', () => {
    expect(formatDate('2026-10-12T10:00:00Z', 'fr-FR')).toBe('12 oct. 2026');
  });

  it('returns an N/A marker for missing or invalid input', () => {
    expect(formatDate(null)).toBe('N/A');
    expect(formatDate(undefined)).toBe('N/A');
    expect(formatDate('not-a-date')).toBe('N/A');
  });
});
