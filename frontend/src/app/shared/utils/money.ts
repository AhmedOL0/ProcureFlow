/** Minor-units money for display. Pure — covered by Vitest. */
export function formatMinor(amountMinor: number | null | undefined, currency = 'MAD'): string {
  if (amountMinor === null || amountMinor === undefined || !Number.isFinite(amountMinor)) {
    return '—';
  }
  try {
    return new Intl.NumberFormat('en-MA', {
      style: 'currency',
      currency,
      minimumFractionDigits: 2,
    }).format(amountMinor / 100);
  } catch {
    return `${(amountMinor / 100).toFixed(2)} ${currency}`;
  }
}
