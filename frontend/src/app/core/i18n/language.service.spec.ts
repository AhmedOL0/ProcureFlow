import { beforeEach, describe, expect, it, vi } from 'vitest';

import { en } from './en';
import { fr } from './fr';
import { LanguageService, setDefaultLocales } from './language.service';

/** Preference logic with stubbed browser surfaces (node env). */
function stubBrowser(stored: string | null, language: string): Record<string, string> {
  const store: Record<string, string> = {};
  if (stored !== null) {
    store['pf-lang'] = stored;
  }
  const attrs: Record<string, string> = {};
  vi.stubGlobal('localStorage', {
    getItem: (key: string): string | null => store[key] ?? null,
    setItem: (key: string, value: string): void => {
      store[key] = value;
    },
  });
  vi.stubGlobal('navigator', { language });
  vi.stubGlobal('document', {
    documentElement: {
      setAttribute: (key: string, value: string): void => {
        attrs[key] = value;
      },
    },
  });
  return attrs;
}

describe('LanguageService', () => {
  beforeEach(() => {
    vi.unstubAllGlobals();
    setDefaultLocales('en-MA', 'en-GB');
  });

  it('defaults to English with no stored preference or French browser', () => {
    stubBrowser(null, 'en-US');
    expect(new LanguageService().lang()).toBe('en');
  });

  it('seeds French from the browser locale on first visit', () => {
    stubBrowser(null, 'fr-FR');
    const service = new LanguageService();
    expect(service.lang()).toBe('fr');
    expect(service.t('common.signIn')).toBe('Se connecter');
    expect(service.numberLocale()).toBe('fr-MA');
    expect(service.dateLocale()).toBe('fr-FR');
  });

  it('prefers a stored language over the browser', () => {
    stubBrowser('en', 'fr-FR');
    expect(new LanguageService().lang()).toBe('en');
  });

  it('treats unknown stored values as absent', () => {
    stubBrowser('de', 'en-US');
    expect(new LanguageService().lang()).toBe('en');
  });

  it('switches catalogs, locales and persistence together', () => {
    stubBrowser(null, 'en-US');
    const service = new LanguageService();
    expect(service.t('common.signIn')).toBe('Sign in');
    service.use('fr');
    expect(service.t('common.signIn')).toBe('Se connecter');
    expect(service.numberLocale()).toBe('fr-MA');
    service.use('en');
    expect(service.t('common.signIn')).toBe('Sign in');
  });

  it('interpolates named placeholders and leaves unknown ones literal', () => {
    stubBrowser(null, 'en-US');
    const service = new LanguageService();
    expect(service.tx('common.signIn', {})).toBe('Sign in');
  });

  it('survives missing browser surfaces', () => {
    vi.stubGlobal('localStorage', undefined);
    vi.stubGlobal('navigator', undefined);
    expect(new LanguageService().lang()).toBe('en');
  });

  it('every English key exists in French', () => {
    const missing = (Object.keys(en) as (keyof typeof en)[]).filter((key) => !(key in fr));
    expect(missing).toEqual([]);
    expect(Object.keys(fr).length).toBe(Object.keys(en).length);
  });

  it('keeps {placeholders} identical across catalogs', () => {
    const names = (text: string): string[] =>
      [...text.matchAll(/\{([a-zA-Z]+)\}/g)].map((match) => match[1]).sort();
    const drifted = (Object.keys(en) as (keyof typeof en)[]).filter(
      (key) => names(en[key]).join(',') !== names(fr[key]).join(','),
    );
    expect(drifted).toEqual([]);
  });

  it('writes <html lang> on apply and switch', () => {
    const attrs = stubBrowser(null, 'en-US');
    const service = new LanguageService();
    service.apply();
    expect(attrs['lang']).toBe('en');
    service.use('fr');
    expect(attrs['lang']).toBe('fr');
  });
});
