import { describe, expect, it, vi } from 'vitest';

import { accessExpiresInMs, isAccessFresh, SessionTokens } from './auth-tokens';

function jwt(expSeconds: number): string {
  const payload = btoa(JSON.stringify({ exp: expSeconds })).replace(/\+/g, '-').replace(/\//g, '_');
  return `header.${payload}.signature`;
}

function session(accessToken: string, obtainedAgoMs = 0): SessionTokens {
  return { accessToken, refreshToken: 'refresh', expiresInSeconds: 900, obtainedAtMs: Date.now() - obtainedAgoMs };
}

describe('auth-tokens', () => {
  it('reads expiry from the JWT exp claim', () => {
    const exp = Math.floor(Date.now() / 1000) + 600;
    const remaining = accessExpiresInMs(session(jwt(exp)));
    expect(remaining).not.toBeNull();
    expect(remaining as number).toBeGreaterThan(590_000);
    expect(remaining as number).toBeLessThanOrEqual(600_000);
  });

  it('returns null for malformed tokens', () => {
    expect(accessExpiresInMs(null)).toBeNull();
    expect(accessExpiresInMs(session('not-a-jwt'))).toBeNull();
    expect(accessExpiresInMs(session('a.b.c'))).toBeNull();
  });

  it('treats near-expiry tokens as stale', () => {
    vi.useFakeTimers();
    try {
      const now = Date.now();
      expect(isAccessFresh(session(jwt(Math.floor(now / 1000) + 600)), 30_000, now)).toBe(true);
      expect(isAccessFresh(session(jwt(Math.floor(now / 1000) + 10)), 30_000, now)).toBe(false);
    } finally {
      vi.useRealTimers();
    }
  });

  it('falls back to issued lifetime for opaque tokens', () => {
    expect(isAccessFresh(session('opaque-token', 0))).toBe(true);
    expect(isAccessFresh(session('opaque-token', 880_000))).toBe(false);
  });
});
