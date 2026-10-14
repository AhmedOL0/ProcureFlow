import '@angular/compiler';
import { HttpErrorResponse } from '@angular/common/http';
import { describe, expect, it, vi } from 'vitest';

import { LanguageService } from '../../core/i18n/language.service';
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

  it('renders catalogued messages in French without touching English', () => {
    // Status fallbacks bake the default language at parse time (parse has
    // no lang argument), so drive the module default through the service —
    // exactly how the app does it — instead of pretending otherwise.
    vi.stubGlobal('localStorage', {
      getItem: () => 'fr',
      setItem: () => undefined,
    });
    vi.stubGlobal('navigator', { language: 'fr-FR' });
    new LanguageService();
    const failure = parseApiFailure(
      httpFailure(409, { code: 'SUPPLIER_EXISTS', message: 'dup' }),
    );
    expect(userMessageFor(failure, 'fr')).toBe('Un fournisseur portant ce nom existe déjà.');
    expect(userMessageFor(failure, 'en')).toBe('A supplier with this name already exists.');
    expect(userMessageFor(parseApiFailure(httpFailure(403, null)))).toContain('permission');
    expect(userMessageFor(parseApiFailure(httpFailure(0, null)))).toContain('injoignable');
  });

  it('keeps unknown server text over generic fallbacks', () => {
    const failure = parseApiFailure(httpFailure(400, { code: 'SOME_FUTURE_CODE', message: 'Specific detail' }));
    expect(userMessageFor(failure, 'fr')).toBe('Specific detail');
  });
});
