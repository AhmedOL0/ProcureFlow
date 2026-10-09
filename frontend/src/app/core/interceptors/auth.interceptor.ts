import { HttpContextToken, HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, switchMap, throwError } from 'rxjs';

import { AuthService } from '../auth/auth.service';

const RETRIED = new HttpContextToken(() => false);
const PUBLIC_AUTH_PATHS = ['/api/v1/auth/login', '/api/v1/auth/register', '/api/v1/auth/refresh'];

/**
 * Attaches the access token and rotates it once on 401 (single-flight
 * refresh shared in AuthService). A second 401 means the session is dead:
 * log out and surface the error instead of looping.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  if (PUBLIC_AUTH_PATHS.some((path) => req.url.includes(path))) {
    return next(req);
  }
  const token = auth.accessToken();
  const outgoing = token ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : req;
  return next(outgoing).pipe(
    catchError((error: unknown) => {
      if (!(error instanceof HttpErrorResponse) || error.status !== 401 || req.context.get(RETRIED)) {
        if (error instanceof HttpErrorResponse && error.status === 401) {
          auth.logout();
        }
        return throwError(() => error);
      }
      return auth.refreshAccess().pipe(
        switchMap((tokens) =>
          next(
            req.clone({
              context: req.context.set(RETRIED, true),
              setHeaders: { Authorization: `Bearer ${tokens.accessToken}` },
            }),
          ),
        ),
        catchError((refreshError: unknown) => {
          auth.logout();
          return throwError(() => refreshError);
        }),
      );
    }),
  );
};
