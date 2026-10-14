import { getDefaultNumberLocale } from '../../core/i18n/language.service';

/** Minor-units money for display. Pure — covered by Vitest. */
export function formatMinor(
  amountMinor: number | null | undefined,
  currency = 'MAD',
  locale?: string,
): string {
  if (amountMinor === null || amountMinor === undefined || !Number.isFinite(amountMinor)) {
    return 'N/A';
  }
  try {
    return new Intl.NumberFormat(locale ?? getDefaultNumberLocale(), {
      style: 'currency',
      currency,
      minimumFractionDigits: 2,
    }).format(amountMinor / 100);
  } catch {
    return `${(amountMinor / 100).toFixed(2)} ${currency}`;
  }
}
