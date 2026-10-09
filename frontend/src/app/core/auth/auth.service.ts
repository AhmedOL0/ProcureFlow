import { computed, inject, Injectable, signal } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Router } from '@angular/router';
import { catchError, finalize, map, Observable, of, shareReplay, tap, throwError } from 'rxjs';

import { environment } from '../../../environments/environment';
import { components } from '../../../lib/api-types.gen';
import { SessionTokens } from './auth-tokens';

type AuthResponse = components['schemas']['AuthResponse'];
type UserResponse = components['schemas']['UserResponse'];
type LoginRequest = components['schemas']['LoginRequest'];
type RegisterRequest = components['schemas']['RegisterRequest'];

export interface ApiError {
  code?: string;
  message?: string;
}

const SESSION_KEY = 'pf.session.v1';

/**
 * Workspace sessions. Tokens live in localStorage (the backend issues
 * body-carried refresh tokens, so there is no HttpOnly-cookie variant yet —
 * XSS hygiene on our own scripts is the mitigation until the cookie
 * transport lands; see the open question in progress-tracker.md).
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly api = environment.apiUrl;

  private readonly tokens = signal<SessionTokens | null>(readStoredSession());
  readonly currentUser = signal<UserResponse | null>(null);
  readonly isAuthenticated = computed(() => this.currentUser() !== null);

  private refreshInFlight: Observable<SessionTokens> | null = null;

  accessToken(): string | null {
    return this.tokens()?.accessToken ?? null;
  }

  /**
   * Client-side permission hint for hiding actions the backend would
   * reject anyway (403). Guards improve UX; authorization stays server-side.
   */
  hasAuthority(code: string): boolean {
    const token = this.accessToken();
    if (!token) {
      return false;
    }
    const parts = token.split('.');
    if (parts.length !== 3 || !parts[1]) {
      return false;
    }
    try {
      const payload = JSON.parse(atob(parts[1].replace(/-/g, '+').replace(/_/g, '/'))) as {
        authorities?: unknown;
      };
      return Array.isArray(payload.authorities) && (payload.authorities as unknown[]).includes(code);
    } catch {
      return false;
    }
  }

  login(email: string, password: string, tenantSlug?: string): Observable<UserResponse> {
    const body: LoginRequest = { email, password, ...(tenantSlug ? { tenantSlug } : {}) };
    return this.http.post<AuthResponse>(`${this.api}/api/v1/auth/login`, body).pipe(
      map((response) => this.store(response)),
      tap(() => void this.router.navigate([this.returnUrl() ?? '/dashboard'])),
    );
  }

  register(email: string, password: string, tenantSlug: string, tenantName?: string): Observable<UserResponse> {
    const body: RegisterRequest = {
      email,
      password,
      tenantSlug,
      ...(tenantName?.trim() ? { tenantName: tenantName.trim() } : {}),
    };
    return this.http.post<AuthResponse>(`${this.api}/api/v1/auth/register`, body).pipe(
      map((response) => this.store(response)),
      tap(() => void this.router.navigate(['/dashboard'])),
    );
  }

  /** Single-flight refresh shared by the interceptor and direct callers. */
  refreshAccess(): Observable<SessionTokens> {
    const refreshToken = this.tokens()?.refreshToken;
    if (!refreshToken) {
      return throwError(() => new Error('no session'));
    }
    if (!this.refreshInFlight) {
      this.refreshInFlight = this.http
        .post<AuthResponse>(`${this.api}/api/v1/auth/refresh`, { refreshToken })
        .pipe(
          map((response) => this.storeTokens(response)),
          tap({ error: () => this.clear() }),
          finalize(() => (this.refreshInFlight = null)),
          shareReplay(1),
        );
    }
    return this.refreshInFlight;
  }

  /** Rebuilds the session after reload: stored tokens plus a live /me. */
  restore(): Observable<boolean> {
    if (!this.tokens()) {
      return of(false);
    }
    return this.http.get<UserResponse>(`${this.api}/api/v1/auth/me`).pipe(
      tap((user) => this.currentUser.set(user)),
      map(() => true),
      catchError(() => {
        this.clear();
        return of(false);
      }),
    );
  }

  logout(navigate = true): void {
    const refreshToken = this.tokens()?.refreshToken;
    if (refreshToken) {
      this.http.post(`${this.api}/api/v1/auth/logout`, { refreshToken }).subscribe({ error: () => undefined });
    }
    this.clear();
    if (navigate) {
      void this.router.navigate(['/login']);
    }
  }

  apiErrorCode(error: unknown): string | null {
    if (error instanceof HttpErrorResponse) {
      const body = error.error as ApiError | null;
      return typeof body?.code === 'string' ? body.code : null;
    }
    return null;
  }

  private returnUrl(): string | null {
    try {
      return new URLSearchParams(window.location.search).get('returnUrl');
    } catch {
      return null;
    }
  }

  private store(response: AuthResponse): UserResponse {
    const user = (response.user ?? null) as UserResponse | null;
    this.storeTokens(response);
    this.currentUser.set(user);
    return user ?? {};
  }

  private storeTokens(response: AuthResponse): SessionTokens {
    const stored: SessionTokens = {
      accessToken: response.accessToken ?? '',
      refreshToken: response.refreshToken ?? '',
      expiresInSeconds: response.expiresInSeconds ?? 900,
      obtainedAtMs: Date.now(),
    };
    this.tokens.set(stored.accessToken ? stored : null);
    try {
      if (stored.accessToken) {
        localStorage.setItem(SESSION_KEY, JSON.stringify(stored));
      } else {
        localStorage.removeItem(SESSION_KEY);
      }
    } catch {
      // Private-mode storage failure: the session simply won't survive reload.
    }
    return stored;
  }

  private clear(): void {
    this.tokens.set(null);
    this.currentUser.set(null);
    try {
      localStorage.removeItem(SESSION_KEY);
    } catch {
      // Nothing stored, nothing to clear.
    }
  }
}

function readStoredSession(): SessionTokens | null {
  try {
    const raw = localStorage.getItem(SESSION_KEY);
    if (!raw) {
      return null;
    }
    const parsed = JSON.parse(raw) as Partial<SessionTokens>;
    if (!parsed.accessToken || !parsed.refreshToken) {
      return null;
    }
    return {
      accessToken: parsed.accessToken,
      refreshToken: parsed.refreshToken,
      expiresInSeconds: parsed.expiresInSeconds ?? 900,
      obtainedAtMs: parsed.obtainedAtMs ?? Date.now(),
    };
  } catch {
    return null;
  }
}
