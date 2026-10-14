import { Injectable, computed, signal } from '@angular/core';

import { en, type I18nKey } from './en';
import { fr } from './fr';

export type Language = 'en' | 'fr';

const STORAGE_KEY = 'pf-lang';

const catalogs: Record<Language, Record<I18nKey, string>> = { en, fr };

// Module-level defaults for the DI-free formatting utils (money, dates).
// The service below is the only production writer; specs set them
// explicitly per test so nothing leaks between cases.
let defaultNumberLocale = 'en-MA';
let defaultDateLocale = 'en-GB';
let defaultLanguage: Language = 'en';

export function getDefaultLanguage(): Language {
  return defaultLanguage;
}

export function setDefaultLanguage(lang: Language): void {
  defaultLanguage = lang;
}

export function getDefaultNumberLocale(): string {
  return defaultNumberLocale;
}

export function getDefaultDateLocale(): string {
  return defaultDateLocale;
}

export function setDefaultLocales(numberLocale: string, dateLocale: string): void {
  defaultNumberLocale = numberLocale;
  defaultDateLocale = dateLocale;
}

/**
 * UI language: stored preference wins, French browser locale seeds French
 * on first visit, everything else starts English. Dictionaries are typed
 * so a missing French key fails the build. Templates call `t()`, which
 * reads the signal and re-renders on switch. Switching also re-points the
 * pure formatting utils above.
 */
@Injectable({ providedIn: 'root' })
export class LanguageService {
  readonly lang = signal<Language>(initialLanguage());

  /** BCP 47 tag for numbers/currency (MAD renders correctly in both). */
  readonly numberLocale = computed(() => (this.lang() === 'fr' ? 'fr-MA' : 'en-MA'));

  /** BCP 47 tag for dates. */
  readonly dateLocale = computed(() => (this.lang() === 'fr' ? 'fr-FR' : 'en-GB'));

  constructor() {
    setDefaultLocales(this.numberLocale(), this.dateLocale());
    setDefaultLanguage(this.lang());
  }

  t(key: I18nKey): string {
    return catalogs[this.lang()][key];
  }

  /**
   * Template with `{name}` placeholders filled from vars, e.g.
   * `t('dashboard.awaitingSub', { n: 3 })`. Values render as-is;
   * missing placeholders stay literal so a typo shows, not blank.
   */
  tx(key: I18nKey, vars: Record<string, string | number>): string {
    let out: string = catalogs[this.lang()][key];
    for (const [name, value] of Object.entries(vars)) {
      out = out.split(`{${name}}`).join(String(value));
    }
    return out;
  }

  use(lang: Language): void {
    try {
      localStorage.setItem(STORAGE_KEY, lang);
    } catch {
      // Private mode: session-only, like the theme preference.
    }
    this.lang.set(lang);
    setDefaultLocales(this.numberLocale(), this.dateLocale());
    setDefaultLanguage(lang);
    applyDocumentLang(lang);
  }

  /** Writes the current language to <html lang>. Runs once at boot
   * (APP_INITIALIZER) before first render, so assistive tech announces
   * the right language from the first paint. */
  apply(): void {
    applyDocumentLang(this.lang());
  }
}

function initialLanguage(): Language {
  try {
    const stored = localStorage.getItem(STORAGE_KEY);
    if (stored === 'en' || stored === 'fr') {
      return stored;
    }
  } catch {
    // Unavailable storage: fall through to the browser locale.
  }
  try {
    return navigator.language.toLowerCase().startsWith('fr') ? 'fr' : 'en';
  } catch {
    return 'en';
  }
}

function applyDocumentLang(lang: Language): void {
  if (typeof document !== 'undefined') {
    document.documentElement.setAttribute('lang', lang);
  }
}

// Re-exported for templates that only need the key type.
export type { I18nKey };
