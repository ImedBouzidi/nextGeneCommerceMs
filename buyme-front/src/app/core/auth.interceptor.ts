import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { AuthStorageService } from './auth-storage.service';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  // Don't attach Authorization header for authentication endpoints
  const url = typeof req.url === 'string' ? req.url : '';
  if (url.includes('/api/auth/login') || url.includes('/api/auth/register') || url.endsWith('/auth/login') || url.endsWith('/auth/register')) {
    return next(req);
  }

  // Must be resolved synchronously: `inject` is only valid in the injection context.
  const authStorage = inject(AuthStorageService);
  const router = inject(Router);

  // getValidToken() purges expired / foreign-realm tokens as a side effect and
  // returns null, in which case we deliberately send no Authorization header at all.
  const token = authStorage.getValidToken();

  const authedReq = token
    ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } })
    : req;

  return next(authedReq).pipe(
    catchError((err: HttpErrorResponse) => {
      // Only treat a 401 as an expired session if we actually sent a token.
      // An anonymous visitor getting 401 just means "that endpoint needs an
      // account" — bouncing them to /login for browsing the storefront is wrong,
      // and it used to happen whenever a public page called a protected endpoint.
      if (err?.status === 401 && token) {
        // The token was rejected by a service: it is unusable, drop it entirely
        // so the app no longer looks authenticated.
        authStorage.clearAllTokens();
        if (!url.includes('/auth/login')) {
          void router.navigate(['/login']);
        }
      }
      return throwError(() => err);
    })
  );
};
