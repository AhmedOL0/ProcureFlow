import { Injectable, signal } from '@angular/core';

export type Theme = 'light' | 'dark';

const STORAGE_KEY = 'pf-theme';
const ATTRIBUTE = 'data-theme';

/**
 * Color theme: light default, dark on demand. The stored preference wins;
 * first visit follows the OS. Applied as html[data-theme] so design
 * tokens and the Material dark theme switch atomically, and charts
 * re-read their palette when the signal flips. Persistence failures
 * (private mode) degrade to session-only, never throw.
 */
@Injectable({ providedIn: 'root' })
export class ThemeService {
  readonly theme = signal<Theme>(readStored() ?? readOs());

  /** Applies the current theme to the document. Runs once at boot
   * (APP_INITIALIZER) before first render, so no flash. */
  apply(): void {
    applyTheme(this.theme());
  }

  toggle(): void {
    const next: Theme = this.theme() === 'light' ? 'dark' : 'light';
    try {
      localStorage.setItem(STORAGE_KEY, next);
    } catch {
      // Private mode: the session keeps dark until reload.
    }
    this.theme.set(next);
    applyTheme(next);
  }
}

function readStored(): Theme | null {
  try {
    const stored = localStorage.getItem(STORAGE_KEY);
    return stored === 'light' || stored === 'dark' ? stored : null;
  } catch {
    return null;
  }
}

function readOs(): Theme {
  try {
    return matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
  } catch {
    return 'light';
  }
}

function applyTheme(theme: Theme): void {
  if (typeof document !== 'undefined') {
    document.documentElement.setAttribute(ATTRIBUTE, theme);
  }
}
