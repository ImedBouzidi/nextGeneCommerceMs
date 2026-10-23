import { HttpErrorResponse, HttpInterceptorFn, HttpRequest } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, switchMap, throwError } from 'rxjs';
import { AuthStorageService } from './auth-storage.service';
import { AuthRefreshService } from './auth-refresh.service';

function isAuthEndpoint(url: string): boolean {
  return (
    url.includes('/api/auth/login') ||
    url.includes('/api/auth/register') ||
    url.includes('/api/auth/refresh') ||
    url.endsWith('/auth/login') ||
    url.endsWith('/auth/register') ||
    url.endsWith('/auth/refresh')
  );
}

function withToken<T>(req: HttpRequest<T>, token: string | null): HttpRequest<T> {
  return token ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : req;
}

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const url = typeof req.url === 'string' ? req.url : '';

  // Auth endpoints are the ones that mint tokens; never attach or refresh for them.
  if (isAuthEndpoint(url)) {
    return next(req);
  }

  // Must be resolved synchronously: `inject` is only valid in the injection context.
  const authStorage = inject(AuthStorageService);
  const authRefresh = inject(AuthRefreshService);
  const router = inject(Router);

  // getValidToken() returns null for an expired token but keeps the refresh token
  // around, so the 401 handler below can recover the session silently.
  const token = authStorage.getValidToken();

  return next(withToken(req, token)).pipe(
    catchError((err: HttpErrorResponse) => {
      if (err?.status !== 401) {
        return throwError(() => err);
      }

      /*
       * A 401 after the access token expired (or was rejected) is recoverable as
       * long as Keycloak gave us a refresh token. Retry the original request once
       * with the new token. The retry calls `next` directly, so it cannot loop
       * back into this interceptor.
       */
      if (authStorage.getRefreshToken()) {
        return authRefresh.refresh().pipe(
          switchMap((newToken) => next(withToken(req, newToken))),
          catchError((refreshErr) => {
            authStorage.clearAllTokens();
            if (!url.includes('/auth/login')) {
              void router.navigate(['/login']);
            }
            return throwError(() => refreshErr);
          })
        );
      }

      // No refresh token: this is a genuine expired session (or an anonymous
      // visitor hitting a protected endpoint). Only a user who used to be signed
      // in should be bounced to the login page; anonymous storefront browsing
      // must keep working.
      if (token || authStorage.getRoles().length > 0) {
        authStorage.clearAllTokens();
        if (!url.includes('/auth/login')) {
          void router.navigate(['/login']);
        }
      }

      return throwError(() => err);
    })
  );
};
