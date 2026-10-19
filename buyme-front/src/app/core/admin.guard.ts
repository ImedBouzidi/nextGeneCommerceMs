import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthStorageService } from './auth-storage.service';

/**
 * Guards the admin area.
 *
 * The `/admin/*` routes were previously unguarded, so any visitor could reach them
 * by typing the URL. This is UX and defence-in-depth only — the real authorisation
 * boundary is the `@PreAuthorize("hasRole('ADMIN')")` checks in the backend
 * services, which reject the request regardless of what the router allows.
 */
export const adminGuard: CanActivateFn = () => {
  const router = inject(Router);
  const authStorage = inject(AuthStorageService);

  const roles = authStorage.getRoles();
  const isAdmin = roles.some(role => role.replace(/^ROLE_/, '').toUpperCase() === 'ADMIN');

  if (isAdmin) return true;

  // Not signed in at all → send to login and remember where they were headed.
  // Signed in but not an admin → send to the storefront, not the login page.
  return authStorage.isAuthenticated()
    ? router.createUrlTree(['/client/home'])
    : router.createUrlTree(['/login']);
};
