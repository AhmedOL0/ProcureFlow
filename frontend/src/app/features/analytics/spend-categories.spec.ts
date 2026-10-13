import { describe, expect, it } from 'vitest';

import { toSpendCategories } from './spend-categories';

describe('toSpendCategories', () => {
  it('maps the analytics wire shape', () => {
    expect(toSpendCategories([{ category: 'Cloud', currency: 'EUR', amountMinor: 1200 }])).toEqual([
      { category: 'Cloud', currency: 'EUR', amountMinor: 1200 },
    ]);
  });

  it('defaults missing currency to MAD', () => {
    expect(toSpendCategories([{ category: 'Cloud', amountMinor: 1200 }])).toEqual([
      { category: 'Cloud', currency: 'MAD', amountMinor: 1200 },
    ]);
  });

  it('repairs missing or malformed fields instead of crashing', () => {
    expect(toSpendCategories([{ amountMinor: Number.NaN }, null, 'x'])).toEqual([
      { category: 'Uncategorized', currency: 'MAD', amountMinor: 0 },
      { category: 'Uncategorized', currency: 'MAD', amountMinor: 0 },
      { category: 'Uncategorized', currency: 'MAD', amountMinor: 0 },
    ]);
  });

  it('rejects non-arrays', () => {
    expect(toSpendCategories(null)).toEqual([]);
    expect(toSpendCategories({})).toEqual([]);
  });
});
