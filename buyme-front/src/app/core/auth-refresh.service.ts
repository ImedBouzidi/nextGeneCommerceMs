import { Inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, finalize, map, shareReplay, throwError } from 'rxjs';
import { APP_CONFIG } from '../../main';
import { AuthStorageService } from './auth-storage.service';

interface RefreshResponse {
  token: string | null;
  refreshToken?: string | null;
  expiresIn?: number | null;
  message?: string;
}

/**
 * Exchanges the stored Keycloak refresh token for a new access token.
 *
 * Keycloak access tokens live five minutes by default, so a long admin session
 * would otherwise fail with 401 on the next write. A single in-flight request is
 * shared by all callers: if five requests hit 401 at once, only one refresh call
 * is made and every request is retried with the resulting token.
 */
@Injectable({ providedIn: 'root' })
export class AuthRefreshService {
  private readonly refreshUrl: string;
  private inFlight$: Observable<string> | null = null;

  constructor(
    private http: HttpClient,
    private authStorage: AuthStorageService,
    @Inject(APP_CONFIG) config: any
  ) {
    const base = (config?.apiUrl || '').replace(/\/$/, '');
    this.refreshUrl = `${base}/api/auth/refresh`;
  }

  refresh(): Observable<string> {
    if (this.inFlight$) return this.inFlight$;

    const refreshToken = this.authStorage.getRefreshToken();
    if (!refreshToken) {
      return throwError(() => new Error('No refresh token available'));
    }

    this.inFlight$ = this.http
      .post<RefreshResponse>(this.refreshUrl, { refreshToken })
      .pipe(
        map((response) => {
          const token = response?.token;
          if (!token || !AuthStorageService.decode(token)) {
            throw new Error('Refresh response did not contain a usable access token');
          }
          this.authStorage.updateAccessToken(token, response.refreshToken ?? refreshToken);
          return token;
        }),
        finalize(() => {
          this.inFlight$ = null;
        }),
        shareReplay({ bufferSize: 1, refCount: false })
      );

    return this.inFlight$;
  }
}
