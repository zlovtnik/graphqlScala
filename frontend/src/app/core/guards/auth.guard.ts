import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService, AuthState } from '../services/auth.service';
import { filter, take, map } from 'rxjs/operators';

/**
 * Auth Guard Function
 * 
 * Protects routes that require authentication by:
 * 1. Waiting for the initial auth state check to complete (filtering out LOADING state)
 * 2. Checking if the user is AUTHENTICATED
 * 3. If UNAUTHENTICATED, redirecting to the login page
 * 4. If AUTHENTICATED, allowing navigation to proceed
 * 
 * This guard prevents false-positive redirects during app startup by waiting
 * for loadCurrentUser() to complete before making routing decisions.
 * 
 * Usage: Add to route definition:
 *   { path: 'protected', component: ProtectedComponent, canActivate: [authGuard] }
 */
export const authGuard: CanActivateFn = (route, state) => {
  const authService = inject(AuthService);
  const router = inject(Router);

  return authService.getAuthState$().pipe(
    // Wait for auth state to be determined (skip LOADING state)
    filter(authState => authState !== AuthState.LOADING),
    // Take only the first determined state
    take(1),
    map(authState => {
      if (authState === AuthState.AUTHENTICATED) {
        return true;
      } else {
        // Redirect to login with return URL
        router.navigate(['/login'], { queryParams: { returnUrl: state.url } });
        return false;
      }
    })
  );
};
