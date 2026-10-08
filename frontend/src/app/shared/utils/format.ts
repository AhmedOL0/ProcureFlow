/**
 * Pure formatting helpers shared across features. Kept framework-free so
 * they are unit-testable with Vitest without Angular TestBed.
 */
export function formatMoney(amountMinor: number, currency = 'MAD'): string {
  const major = amountMinor / 100;
  return new Intl.NumberFormat('en-MA', {
    style: 'currency',
    currency,
    minimumFractionDigits: 2,
  }).format(major);
}

export function truncate(text: string, maxLength = 80): string {
  if (text.length <= maxLength) {
    return text;
  }
  return `${text.slice(0, maxLength - 1).trimEnd()}…`;
}
