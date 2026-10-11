import { describe, expect, it, vi } from 'vitest';
import { of, throwError } from 'rxjs';

import { averageOnTime, enrichSuppliers, initialsFor, latestScoreOf } from './supplier-rows';
import type { SuppliersService } from './suppliers.service';

describe('supplier-rows', () => {
  it('builds avatar initials', () => {
    expect(initialsFor('Acme Parts')).toBe('AP');
    expect(initialsFor('Solo')).toBe('S');
    expect(initialsFor('  ')).toBe('?');
    expect(initialsFor(null)).toBe('?');
  });

  it('averages present on-time rates', () => {
    expect(averageOnTime([{ onTimeRate: 80 }, { onTimeRate: 100 }] as never[])).toBe(90);
    expect(averageOnTime([{ onTimeRate: null }] as never[])).toBeNull();
    expect(averageOnTime([])).toBeNull();
  });

  it('picks the latest scorecard by period', () => {
    const rows = [
      { period: '2026-02', onTimeRate: 100 },
      { period: '2026-01', onTimeRate: 80 },
    ] as never[];
    expect(latestScoreOf(rows)?.period).toBe('2026-02');
    expect(latestScoreOf([])).toBeNull();
  });

  it('enriches rows and tolerates empty sections', () => {
    const service = {
      contacts: vi.fn().mockReturnValue(of([{ id: 'c1', name: 'Ada', primary: true }])),
      categoriesOf: vi.fn().mockReturnValue(of([])),
      performances: vi.fn().mockReturnValue(of([{ period: '2026-01', onTimeRate: 80 }])),
    } as unknown as SuppliersService;
    let result: unknown;
    enrichSuppliers(service, [{ id: 's1', name: 'Acme' }] as never[]).subscribe((rows) => (result = rows));
    expect(result).toEqual([
      {
        supplier: { id: 's1', name: 'Acme' },
        primaryContact: { id: 'c1', name: 'Ada', primary: true },
        categories: [],
        latestScore: { period: '2026-01', onTimeRate: 80 },
        averageOnTime: 80,
      },
    ]);
  });

  it('keeps the row when a section fails or misbehaves', () => {
    const service = {
      contacts: vi.fn().mockReturnValue(of({ content: [], totalElements: 0 })),
      categoriesOf: vi.fn().mockReturnValue(throwError(() => new Error('down'))),
      performances: vi.fn().mockReturnValue(of([])),
    } as unknown as SuppliersService;
    let result: unknown;
    let failed = false;
    enrichSuppliers(service, [{ id: 's1', name: 'Acme' }] as never[]).subscribe({
      next: (rows) => (result = rows),
      error: () => (failed = true),
    });
    expect(failed).toBe(false);
    expect(result).toEqual([
      {
        supplier: { id: 's1', name: 'Acme' },
        primaryContact: null,
        categories: [],
        latestScore: null,
        averageOnTime: null,
      },
    ]);
  });
});
