import { describe, expect, it } from 'vitest';
import { formatMoney, truncate } from './format';

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
