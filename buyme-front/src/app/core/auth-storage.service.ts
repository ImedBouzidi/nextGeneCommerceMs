import { Inject, Injectable } from '@angular/core';
import { APP_CONFIG } from '../../main';

/** Storage keys that belong to authentication state. */
export const AUTH_STORAGE_KEYS = ['jwt_token', 'role', 'authToken', 'currentUser'] as const;

/** Keys holding per-user state that must not survive a logout. */
export const SESSION_STORAGE_KEYS = ['cart_items', 'user_discount_percent'] as const;

/** Keys that must survive any auth purge (theme preference). */
export const PRESERVED_STORAGE_KEYS = ['buyme.theme'] as const;

interface DecodedJwt {
  iss?: string;
  exp?: number;
  sub?: string;
  preferred_username?: string;
  [claim: string]: any;
}

/**
 * Single source of truth for every token the app keeps in localStorage.
 *
 * Nothing else in the app should call localStorage.setItem/removeItem for auth
 * state. That is how foreign-realm tokens used to survive logout: several
 * components removed keys (`authToken`, `currentUser`) that were never written,
 * while the real token (`jwt_token`) stayed put and kept being attached to every
 * request by the auth interceptor.
 */
@Injectable({ providedIn: 'root' })
export class AuthStorageService {
  private readonly expectedIssuer: string | null;
  private readonly expectedRealm: string | null;
  /** Opt-in exact-issuer matching; off by default because `iss` is host-dependent. */
  private readonly strictIssuer: boolean;

  constructor(@Inject(APP_CONFIG) config: any) {
    this.expectedIssuer = AuthStorageService.normalizeIssuer(config?.issuer);
    this.expectedRealm = AuthStorageService.normalizeIssuer(config?.realm);
    this.strictIssuer = config?.strictIssuer === true;
  }

  /**
   * Normalizes an issuer URL to a comparable lowercase form without a trailing
   * slash. Returns null when the value is absent/blank (validation disabled).
   */
  private static normalizeIssuer(value: string | null | undefined): string | null {
    if (!value) return null;
    const trimmed = String(value).trim().replace(/\/+$/, '').toLowerCase();
    return trimmed || null;
  }

  private static realmOf(issuer: string | null | undefined): string | null {
    if (!issuer) return null;
    const match = /\/realms\/([^/?#]+)/i.exec(issuer);
    return match ? match[1].toLowerCase() : null;
  }

  /** Decodes a JWT payload without verifying the signature (not possible client-side). */
  static decode(token: string | null | undefined): DecodedJwt | null {
    if (!token) return null;
    const parts = token.split('.');
    if (parts.length < 2) return null;
    try {
      const base64 = parts[1].replace(/-/g, '+').replace(/_/g, '/');
      const padded = base64.padEnd(base64.length + ((4 - (base64.length % 4)) % 4), '=');
      return JSON.parse(atob(padded));
    } catch {
      return null;
    }
  }

  /**
   * A value is treated as a stored token if it is a well-formed JWT carrying at
   * least an `iss` or `exp` claim. This catches tokens written under keys we do
   * not know about (e.g. leftovers from an older realm or a previous refactor).
   */
  private static looksLikeJwt(value: string | null): boolean {
    if (!value) return false;
    const trimmed = value.trim();
    if (trimmed.split('.').length !== 3) return false;
    const decoded = AuthStorageService.decode(trimmed);
    return !!decoded && ('iss' in decoded || 'exp' in decoded);
  }

  getToken(): string | null {
    return localStorage.getItem('jwt_token');
  }

  getRoles(): string[] {
    const raw = localStorage.getItem('role');
    return raw ? raw.split(',').map(r => r.trim()).filter(Boolean) : [];
  }

  /**
   * Returns the stored token only if it is present, unexpired and issued by the
   * configured realm. Anything else is treated as absent and purged, so a token
   * from another realm (gomrah, master, ...) is never sent.
   */
  getValidToken(): string | null {
    const token = this.getToken();
    if (!token) return null;

    const decoded = AuthStorageService.decode(token);
    if (!decoded) {
      // Not a JWT at all — malformed/foreign value sitting in the token slot.
      this.clearAllTokens();
      return null;
    }

    if (decoded.exp && decoded.exp * 1000 <= Date.now()) {
      this.clearAllTokens();
      return null;
    }

    /*
     * Realm-name comparison is the primary check: it is host-independent, so it
     * keeps working whether Keycloak is reached at localhost:8080 (local run) or
     * keycloak:8080 (docker), where the `iss` claim differs from the one a
     * browser-side config would guess. Realm names are what separates the
     * e-commerce realm from the stray gomrah/master realms on the same server.
     */
    if (this.expectedRealm) {
      const tokenRealm = AuthStorageService.realmOf(decoded.iss);
      if (!tokenRealm || tokenRealm !== this.expectedRealm) {
        this.clearAllTokens();
        return null;
      }
    }

    // Optional strict mode: also require the exact issuer URL to match.
    if (this.strictIssuer && this.expectedIssuer &&
        AuthStorageService.normalizeIssuer(decoded.iss) !== this.expectedIssuer) {
      this.clearAllTokens();
      return null;
    }

    return token;
  }

  isAuthenticated(): boolean {
    return this.getValidToken() !== null;
  }

  /**
   * Clears every token: the known auth keys plus any localStorage entry that
   * looks like a JWT, whichever key it is stored under.
   */
  clearAllTokens(): void {
    for (const key of AUTH_STORAGE_KEYS) {
      localStorage.removeItem(key);
    }

    for (let i = localStorage.length - 1; i >= 0; i--) {
      const key = localStorage.key(i);
      if (!key) continue;
      if ((PRESERVED_STORAGE_KEYS as readonly string[]).includes(key)) continue;
      if (AUTH_STORAGE_KEYS.includes(key as any)) continue;
      if (AuthStorageService.looksLikeJwt(localStorage.getItem(key))) {
        localStorage.removeItem(key);
      }
    }
  }

  /** Also drops per-user state (cart, discount). Used on logout. */
  clearSession(): void {
    this.clearAllTokens();
    for (const key of SESSION_STORAGE_KEYS) {
      localStorage.removeItem(key);
    }
  }

  /**
   * Replaces any existing session with the newly issued one. Purging first
   * guarantees no token from a previous realm survives the login.
   */
  setSession(token: string, roles: string[] | null): void {
    this.clearAllTokens();
    localStorage.setItem('jwt_token', token);
    localStorage.setItem('role', (roles || []).join(','));
  }
}
