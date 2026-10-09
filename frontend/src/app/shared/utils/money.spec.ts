import { describe, expect, it } from 'vitest';

import { formatMinor } from './money';

describe('money', () => {
  it('formats minor units with currency', () => {
    expect(formatMinor(179800, 'MAD')).toContain('1,798.00');
    expect(formatMinor(179800, 'MAD')).toContain('MAD');
    expect(formatMinor(0, 'EUR')).toContain('0.00');
  });

  it('handles missing values and currencies', () => {
    expect(formatMinor(null)).toBe('—');
    expect(formatMinor(undefined)).toBe('—');
    expect(formatMinor(NaN)).toBe('—');
    expect(formatMinor(100, 'XX')).toContain('1.00');
  });
});
