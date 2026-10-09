// Pure session-token helpers: no Angular, no I/O — covered by Vitest.

export interface SessionTokens {
  accessToken: string;
  refreshToken: string;
  /** Seconds the access token lives, from the moment it was obtained. */
  expiresInSeconds: number;
  obtainedAtMs: number;
}

/** Milliseconds until the access token expires, or null when unreadable. */
export function accessExpiresInMs(tokens: Pick<SessionTokens, 'accessToken'> | null): number | null {
  if (!tokens?.accessToken) {
    return null;
  }
  const parts = tokens.accessToken.split('.');
  if (parts.length !== 3 || !parts[1]) {
    return null;
  }
  try {
    const payload = JSON.parse(base64UrlDecode(parts[1]) ?? 'null') as { exp?: unknown };
    if (typeof payload?.exp !== 'number') {
      return null;
    }
    return payload.exp * 1000 - Date.now();
  } catch {
    return null;
  }
}

/** True when the token plausibly survives the next request (30s skew). */
export function isAccessFresh(
  tokens: SessionTokens | null,
  skewMs = 30_000,
  nowMs: number = Date.now(),
): boolean {
  if (!tokens?.accessToken) {
    return false;
  }
  const parts = tokens.accessToken.split('.');
  if (parts.length !== 3 || !parts[1]) {
    // Opaque token: fall back to the issued lifetime.
    return nowMs - tokens.obtainedAtMs < (tokens.expiresInSeconds * 1000 - skewMs);
  }
  try {
    const payload = JSON.parse(base64UrlDecode(parts[1]) ?? 'null') as { exp?: unknown };
    if (typeof payload?.exp !== 'number') {
      return false;
    }
    return payload.exp * 1000 - nowMs > skewMs;
  } catch {
    return false;
  }
}

function base64UrlDecode(segment: string): string | null {
  try {
    const padded = segment.replace(/-/g, '+').replace(/_/g, '/');
    return atob(padded);
  } catch {
    return null;
  }
}
