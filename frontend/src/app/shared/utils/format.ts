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
  return `${text.slice(0, maxLength - 3).trimEnd()}...`;
}

/**
 * Short human date for table cells, e.g. "12 Oct 2026". Returns an N/A
 * marker for missing/invalid input — never throws on server strings.
 */
export function formatDate(value: string | null | undefined): string {
  if (!value) {
    return 'N/A';
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return 'N/A';
  }
  return new Intl.DateTimeFormat('en-GB', {
    day: 'numeric',
    month: 'short',
    year: 'numeric',
  }).format(date);
}
