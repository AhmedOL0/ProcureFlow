/**
 * One bar of the spend-by-category breakdown. NOTE: the generated
 * `CategoryResponse` type collides with the supplier-category shape
 * ({id,name,description}); the analytics wire shape is
 * {category,currency,amountMinor} (see AnalyticsController.CategoryResponse), so
 * this interface describes the runtime truth and `toSpendCategories`
 * normalizes defensively. Pure — covered by Vitest.
 */
export interface SpendCategory {
  category: string;
  currency: string;
  amountMinor: number;
}

export function toSpendCategories(rows: unknown): SpendCategory[] {
  if (!Array.isArray(rows)) {
    return [];
  }
  return rows.map((row) => {
    const record = typeof row === 'object' && row !== null ? (row as Record<string, unknown>) : {};
    const category = record['category'];
    const currency = record['currency'];
    const amount = record['amountMinor'];
    return {
      category: typeof category === 'string' && category !== '' ? category : 'Uncategorized',
      currency: typeof currency === 'string' && currency !== '' ? currency : 'MAD',
      amountMinor: typeof amount === 'number' && Number.isFinite(amount) ? amount : 0,
    };
  });
}
