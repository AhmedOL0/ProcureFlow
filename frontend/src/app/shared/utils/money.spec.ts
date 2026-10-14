import { describe, expect, it } from 'vitest';

import { formatMinor } from './money';

describe('money', () => {
  it('formats minor units with currency', () => {
    expect(formatMinor(179800, 'MAD')).toContain('1,798.00');
    expect(formatMinor(179800, 'MAD')).toContain('MAD');
    expect(formatMinor(0, 'EUR')).toContain('0.00');
  });

  it('handles missing values and currencies', () => {
    expect(formatMinor(null)).toBe('N/A');
    expect(formatMinor(undefined)).toBe('N/A');
    expect(formatMinor(NaN)).toBe('N/A');
    expect(formatMinor(100, 'XX')).toContain('1.00');
  });

  it('formats with an explicit French locale', () => {
    expect(formatMinor(179800, 'MAD', 'fr-MA')).toContain('798,00');
  });
});
