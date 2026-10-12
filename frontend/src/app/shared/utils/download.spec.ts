import { describe, expect, it } from 'vitest';
import { signal } from '@angular/core';
import { throwError } from 'rxjs';

import { csvCell, filenameFromDisposition, runDownload, sanitizeCsvCell } from './download';

describe('sanitizeCsvCell', () => {
  it('prefixes formula triggers with the text marker', () => {
    expect(sanitizeCsvCell('=cmd')).toBe("'=cmd");
    expect(sanitizeCsvCell('+123')).toBe("'+123");
    expect(sanitizeCsvCell('@mention')).toBe("'@mention");
  });

  it('leaves plain values and empties alone', () => {
    expect(sanitizeCsvCell('Acme Parts')).toBe('Acme Parts');
    expect(sanitizeCsvCell(1500.5)).toBe('1500.5');
    expect(sanitizeCsvCell(null)).toBe('');
    expect(sanitizeCsvCell(undefined)).toBe('');
  });
});

describe('csvCell', () => {
  it('quotes cells and doubles embedded quotes', () => {
    expect(csvCell('a,b')).toBe('"a,b"');
    expect(csvCell('say "hi"')).toBe('"say ""hi"""');
  });
});

describe('filenameFromDisposition', () => {  it('prefers the server filename', () => {
    expect(filenameFromDisposition('attachment; filename="suppliers-demo-2026-10-12.xlsx"', 'fallback.xlsx')).toBe(
      'suppliers-demo-2026-10-12.xlsx',
    );
  });

  it('falls back and strips hostile names', () => {
    expect(filenameFromDisposition(null, 'fallback.xlsx')).toBe('fallback.xlsx');
    expect(filenameFromDisposition('attachment; filename="../../evil.exe"', 'fallback.xlsx')).toBe('evil.exe');
    expect(filenameFromDisposition('attachment', '')).toBe('');
  });
});

describe('runDownload', () => {
  it('clears busy and surfaces the mapped message on failure', () => {
    const busy = signal(false);
    const failed = signal<string | null>(null);
    runDownload(
      throwError(() => new Error('boom')),
      busy,
      failed,
      () => 'Export failed. Try again.',
    );
    expect(busy()).toBe(false);
    expect(failed()).toBe('Export failed. Try again.');
  });
});
