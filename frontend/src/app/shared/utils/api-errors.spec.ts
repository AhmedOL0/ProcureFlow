import '@angular/compiler';
import { HttpErrorResponse } from '@angular/common/http';
import { describe, expect, it } from 'vitest';

import { parseApiFailure, userMessageFor } from './api-errors';

function httpFailure(status: number, body: unknown): HttpErrorResponse {
  return new HttpErrorResponse({ status, error: body });
}

describe('api-errors', () => {
  it('reads the backend envelope', () => {
    const failure = parseApiFailure(httpFailure(409, { code: 'SUPPLIER_EXISTS', message: 'dup' }));
    expect(failure).toEqual({ status: 409, code: 'SUPPLIER_EXISTS', message: 'dup', details: {} });
    expect(userMessageFor(failure)).toBe('A supplier with this name already exists.');
  });

  it('carries per-row details for import failures', () => {
    const failure = parseApiFailure(
      httpFailure(400, {
        code: 'IMPORT_VALIDATION_FAILED',
        message: 'fix the rows',
        details: { 'row.2.email': 'Not a valid email address', errorCount: '1' },
      }),
    );
    expect(failure.details).toEqual({ 'row.2.email': 'Not a valid email address', errorCount: '1' });
    expect(userMessageFor(failure)).toContain('Nothing was imported');
  });

  it('falls back per status without an envelope', () => {
    expect(parseApiFailure(httpFailure(0, null)).message).toContain('unreachable');
    expect(parseApiFailure(httpFailure(401, null)).message).toContain('expired');
    expect(parseApiFailure(httpFailure(403, null)).message).toContain('permission');
    expect(parseApiFailure(httpFailure(404, null)).message).toContain('workspace');
    expect(userMessageFor(parseApiFailure(httpFailure(403, null)))).toContain('permission');
  });

  it('handles non-HTTP failures', () => {
    const failure = parseApiFailure(new Error('boom'));
    expect(failure.status).toBe(0);
    expect(failure.code).toBeNull();
  });
});
