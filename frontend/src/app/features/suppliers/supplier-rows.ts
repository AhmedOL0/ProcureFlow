import { catchError, forkJoin, map, Observable, of } from 'rxjs';

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
        contacts: service.contacts(id).pipe(catchError(() => of([]))),
        categories: service.categoriesOf(id).pipe(catchError(() => of([]))),
        performances: service.performances(id).pipe(catchError(() => of([]))),
      }).pipe(
        map(({ contacts, categories, performances }) => {
          const contactList = Array.isArray(contacts) ? contacts : [];
          const categoryList = Array.isArray(categories) ? categories : [];
          const scoreList = Array.isArray(performances) ? performances : [];
          return {
            supplier,
            primaryContact: contactList.find((c) => c.primary) ?? null,
            categories: categoryList,
            latestScore: latestScoreOf(scoreList),
            averageOnTime: averageOnTime(scoreList),
          };
        }),
      );
    }),
  );
}
