import { describe, expect, it, vi } from 'vitest';

import { ThemeService } from './theme.service';

/** Theme preference logic with stubbed browser surfaces (node env). */
function stubBrowser(stored: string | null, prefersDark: boolean): Record<string, string> {
  const store: Record<string, string> = {};
  if (stored !== null) {
    store['pf-theme'] = stored;
  }
  const attrs: Record<string, string> = {};
  vi.stubGlobal('localStorage', {
    getItem: (key: string): string | null => store[key] ?? null,
    setItem: (key: string, value: string): void => {
      store[key] = value;
    },
  });
  vi.stubGlobal('matchMedia', () => ({ matches: prefersDark }));
  vi.stubGlobal('document', {
    documentElement: {
      setAttribute: (key: string, value: string): void => {
        attrs[key] = value;
      },
    },
  });
  return attrs;
}

describe('ThemeService', () => {
  it('defaults to light with no stored preference or OS signal', () => {
    stubBrowser(null, false);
    expect(new ThemeService().theme()).toBe('light');
  });

  it('follows a stored preference over the OS', () => {
    stubBrowser('dark', false);
    expect(new ThemeService().theme()).toBe('dark');
  });

  it('seeds dark from the OS on first visit', () => {
    stubBrowser(null, true);
    expect(new ThemeService().theme()).toBe('dark');
  });

  it('treats unknown stored values as absent', () => {
    stubBrowser('midnight', true);
    expect(new ThemeService().theme()).toBe('dark');
  });

  it('toggles, persists and applies the attribute', () => {
    const attrs = stubBrowser(null, false);
    const service = new ThemeService();
    service.apply();
    expect(attrs['data-theme']).toBe('light');
    service.toggle();
    expect(service.theme()).toBe('dark');
    expect(attrs['data-theme']).toBe('dark');
    service.toggle();
    expect(service.theme()).toBe('light');
  });
});
