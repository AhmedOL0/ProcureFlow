import { forkJoin, map, Observable, of } from 'rxjs';

import { Supplier, SupplierCategory, SupplierContact, SupplierPerformance, SuppliersService } from './suppliers.service';

export { initialsFor } from '../../shared/utils/names';

/** One directory row with everything the table renders, resolved up front. */
export interface SupplierRow {
  supplier: Supplier;
  primaryContact: SupplierContact | null;
  categories: SupplierCategory[];
  /** Most recent scorecard by period, if any. */
  latestScore: SupplierPerformance | null;
  averageOnTime: number | null;
}

/** Mean of present on-time rates, rounded to one decimal, else null. */
export function averageOnTime(scores: SupplierPerformance[]): number | null {
  const rates = scores.map((s) => s.onTimeRate).filter((r): r is number => typeof r === 'number');
  if (rates.length === 0) {
    return null;
  }
  return Math.round((rates.reduce((a, b) => a + b, 0) / rates.length) * 10) / 10;
}

/** Latest scorecard by YYYY-MM period, else null. */
export function latestScoreOf(scores: SupplierPerformance[]): SupplierPerformance | null {
  const dated = scores.filter((s) => typeof s.period === 'string');
  if (dated.length === 0) {
    return null;
  }
  return dated.sort((a, b) => (a.period as string).localeCompare(b.period as string)).at(-1) ?? null;
}

/** Enriches every row in parallel; a section failure empties that section, never the row. */
export function enrichSuppliers(service: SuppliersService, suppliers: Supplier[]): Observable<SupplierRow[]> {
  if (suppliers.length === 0) {
    return of([]);
  }
  return forkJoin(
    suppliers.map((supplier) => {
      const id = supplier.id ?? '';
      return forkJoin({
        contacts: service.contacts(id),
        categories: service.categoriesOf(id),
        performances: service.performances(id),
      }).pipe(
        map(({ contacts, categories, performances }) => ({
          supplier,
          primaryContact: (contacts ?? []).find((c) => c.primary) ?? null,
          categories: categories ?? [],
          latestScore: latestScoreOf(performances ?? []),
          averageOnTime: averageOnTime(performances ?? []),
        })),
      );
    }),
  );
}
