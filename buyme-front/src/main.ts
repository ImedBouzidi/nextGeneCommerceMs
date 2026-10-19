/*import { bootstrapApplication } from '@angular/platform-browser';
import { appConfig } from './app/app.config';
import { AppComponent } from './app/app.component';

bootstrapApplication(AppComponent, appConfig)
  .catch((err) => console.error(err));*/

import {InjectionToken} from '@angular/core';
import {bootstrapApplication} from '@angular/platform-browser';
import {provideHttpClient, withInterceptors} from '@angular/common/http';
import {AppComponent} from './app/app.component';
import {authInterceptor} from './app/core/auth.interceptor';
import {provideRouter} from '@angular/router';
import {routes} from './app/app.routes';

export const APP_CONFIG = new InjectionToken<any>('APP_CONFIG');

const FALLBACK_CONFIG = {
  apiUrl: 'http://localhost:8081/api',
  realm: 'e-commerce',
  issuer: 'http://localhost:8080/realms/e-commerce'
};

/**
 * Startup cleanup of tokens left behind by a previous version or by a different
 * Keycloak realm. A token that is still valid for the configured realm is kept,
 * so a normal page refresh does not log the user out.
 *
 * Mirrors AuthStorageService: any localStorage entry that parses as a JWT with
 * an `iss`/`exp` claim but is not from our realm gets removed.
 */
function purgeForeignTokens(config: any): void {
  const PRESERVED = ['buyme.theme'];
  const expectedRealm = (config?.realm || '').trim().toLowerCase();
  const decode = (token: string): any => {
    const parts = token.split('.');
    if (parts.length !== 3) return null;
    try {
      const base64 = parts[1].replace(/-/g, '+').replace(/_/g, '/');
      const padded = base64.padEnd(base64.length + ((4 - (base64.length % 4)) % 4), '=');
      return JSON.parse(atob(padded));
    } catch {
      return null;
    }
  };
  const isOurs = (claims: any): boolean => {
    if (!claims) return false;
    // Realm-name comparison (host-independent, so docker/local both work).
    const realmMatch = /\/realms\/([^/?#]+)/i.exec(claims.iss || '');
    const realm = realmMatch ? realmMatch[1].toLowerCase() : '';
    if (!expectedRealm || realm !== expectedRealm) return false;
    if (claims.exp && claims.exp * 1000 <= Date.now()) return false;
    return true;
  };

  try {
    for (let i = localStorage.length - 1; i >= 0; i--) {
      const key = localStorage.key(i);
      if (!key || PRESERVED.includes(key)) continue;
      const claims = decode((localStorage.getItem(key) || '').trim());
      if (!claims || !('iss' in claims || 'exp' in claims)) continue; // not a token
      if (!isOurs(claims)) {
        localStorage.removeItem(key);
        console.info(`[auth] removed stale token "${key}" (realm mismatch or expired)`);
      }
    }
  } catch (e) {
    console.warn('Token purge skipped', e);
  }
}

function bootstrapWith(config: any) {
  purgeForeignTokens(config);
  bootstrapApplication(AppComponent, {
    providers: [
      provideHttpClient(withInterceptors([authInterceptor])),
      provideRouter(routes),
      { provide: APP_CONFIG, useValue: config }
    ]
  });
}

fetch('/assets/config/config.json')
  .then(res => {
    if (!res.ok) throw new Error(`HTTP error! status: ${res.status}`);
    return res.json();
  })
  .then(config => bootstrapWith(config))
  .catch(err => {
    console.error('Failed to load config.json', err);
    bootstrapWith(FALLBACK_CONFIG);
  });
