import { Injectable, PLATFORM_ID, OnDestroy, inject } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { Apollo } from 'apollo-angular';
import { BehaviorSubject, Observable, of, Subscription, throwError } from 'rxjs';
import { map, tap, catchError, take } from 'rxjs/operators';
import {
  LOGIN_MUTATION,
  REGISTER_MUTATION,
  GET_CURRENT_USER_QUERY
} from '../graphql';
import { TokenStorageAdapter } from './token-storage.adapter';
import { RefreshTokenService } from './refresh-token.service';
import { PosthogService } from './posthog.service';

/**
 * Authentication state tri-state enum
 * - LOADING: Initial state, checking if user is authenticated
 * - AUTHENTICATED: User is logged in and validated
 * - UNAUTHENTICATED: User is not logged in or validation failed
 */
export enum AuthState {
  LOADING = 'LOADING',
  AUTHENTICATED = 'AUTHENTICATED',
  UNAUTHENTICATED = 'UNAUTHENTICATED'
}

export interface User {
  id: string;
  username: string;
  email: string;
}

export interface AuthResponse {
  token: string;
  user?: User | null;
}

@Injectable({
  providedIn: 'root'
})
export class AuthService implements OnDestroy {
  private readonly TOKEN_STORAGE_KEY = 'auth-token';
  private currentUser$ = new BehaviorSubject<User | null>(null);
  // Start as LOADING to indicate we're checking authentication status
  private authStateSubject$ = new BehaviorSubject<AuthState>(AuthState.LOADING);
  // Store subscription for cleanup
  private loadCurrentUserSubscription?: Subscription;
  private refreshSuccessSubscription?: Subscription;
  private refreshFailureSubscription?: Subscription;
  private lastIdentifiedUserId: string | null = null;

  private apollo = inject(Apollo);
  private tokenStorage = inject(TokenStorageAdapter);
  private refreshTokenService = inject(RefreshTokenService);
  private posthogService = inject(PosthogService);
  private platformId = inject(PLATFORM_ID);

  constructor() {
    if (isPlatformBrowser(this.platformId)) {
      this.bindRefreshEvents();
      this.loadCurrentUser();
    }
  }

  /**
   * Get current user as observable
   */
  getCurrentUser$(): Observable<User | null> {
    return this.currentUser$.asObservable();
  }

  /**
   * Get current user synchronously
   */
  getCurrentUser(): User | null {
    return this.currentUser$.value;
  }

  /**
   * Get authentication state as observable
   * Returns tri-state: LOADING, AUTHENTICATED, or UNAUTHENTICATED
   */
  getAuthState$(): Observable<AuthState> {
    return this.authStateSubject$.asObservable();
  }

  /**
   * Get authentication state synchronously
   * Returns tri-state: LOADING, AUTHENTICATED, or UNAUTHENTICATED
   */
  getAuthState(): AuthState {
    return this.authStateSubject$.value;
  }

  /**
   * Check if user is authenticated (boolean convenience method)
   * WARNING: May return false during initial load - use getAuthState$() for accurate startup state
   */
  isAuthenticated(): boolean {
    return this.authStateSubject$.value === AuthState.AUTHENTICATED;
  }

  /**
   * Get stored authentication token
   * 
   * SECURITY NOTE: In production, the token is stored in an httpOnly cookie
   * that's not accessible to JavaScript. This method returns null in production.
   * Apollo Client automatically includes the cookie in GraphQL requests.
   */
  getToken(): string | null {
    return this.tokenStorage.getToken();
  }

  /**
   * Login user
   */
  login(username: string, password: string): Observable<AuthResponse> {
    return this.apollo.mutate<{ login: AuthResponse }>({
      mutation: LOGIN_MUTATION,
      variables: { username, password }
    }).pipe(
      map(result => {
        const payload = result.data?.login;
        if (!payload?.token) {
          throw new Error('Invalid login response from server');
        }
        return payload;
      }),
      tap(response => this.setAuthToken(response)),
      catchError(error => throwError(() => new Error(error?.message || 'Login failed')))
    );
  }

  /**
   * Register new user
   */
  register(username: string, email: string, password: string): Observable<AuthResponse> {
    return this.apollo.mutate<{ register: AuthResponse }>({
      mutation: REGISTER_MUTATION,
      variables: { username, email, password }
    }).pipe(
      map(result => {
        if (result.error) {
          throw new Error(result.error.message || 'Registration failed');
        }
        const payload = result.data?.register;
        if (!payload?.token) {
          throw new Error('Invalid register response from server');
        }
        return payload;
      }),
      tap(response => this.setAuthToken(response))
    );
  }

  /**
   * Logout user
   */
  async logout(): Promise<void> {
    this.refreshTokenService.cancelRefresh();
    this.tokenStorage.clearToken();
    this.tokenStorage.markAuthenticated(false);
    this.currentUser$.next(null);
    this.authStateSubject$.next(AuthState.UNAUTHENTICATED);
    // Reset PostHog user tracking (non-blocking)
    // Normalize return value with Promise.resolve() to handle both sync/async returns
    Promise.resolve(this.posthogService.resetUser())
      .catch((error) => {
        console.warn('Failed to reset PostHog user:', error);
      });
    this.lastIdentifiedUserId = null;
    try {
      await this.apollo.client.clearStore();
    } catch (error) {
      console.warn('Failed to clear Apollo cache during logout:', error);
    }
  }

  /**
   * Load current user from server
   * This resolves the initial authentication state (LOADING -> AUTHENTICATED or UNAUTHENTICATED)
   */
  private loadCurrentUser(): void {
    if (!this.hasToken()) {
      // No token - user is unauthenticated
      this.authStateSubject$.next(AuthState.UNAUTHENTICATED);
      return;
    }

    // Clean up any existing subscription
    if (this.loadCurrentUserSubscription) {
      this.loadCurrentUserSubscription.unsubscribe();
    }

    this.loadCurrentUserSubscription = this.apollo.query<{ currentUser: User }>({
      query: GET_CURRENT_USER_QUERY
    }).pipe(
      map(result => result.data?.currentUser),
      tap(user => {
        if (user) {
          this.currentUser$.next(user);
          // Only update auth state if we're still in a loading/indeterminate state
          if (this.authStateSubject$.value === AuthState.LOADING) {
            this.authStateSubject$.next(AuthState.AUTHENTICATED);
          }
          this.identifyUserIfNeeded(user);
        }
        // If no user but auth state is already set, don't change it
        // This allows async user loading without reverting auth state
      }),
      catchError((error) => {
        // Query failed - but keep user authenticated since they have a valid token
        console.warn('Failed to load current user details:', error);
        // Only logout if it's a 401/403 auth error
        if (error?.networkError?.status === 401 || error?.networkError?.status === 403) {
          // Fire logout async without blocking
          this.logout().catch(err => console.warn('Logout failed:', err));
        } else if (this.authStateSubject$.value === AuthState.LOADING) {
          this.authStateSubject$.next(AuthState.UNAUTHENTICATED);
        }
        // For other errors, keep current auth state (likely AUTHENTICATED from setAuthToken)
        return of(null);
      }),
      take(1)
    ).subscribe();
  }

  /**
   * Set authentication token and user
   * 
   * Also schedules proactive token refresh based on JWT expiration.
   */
  private setAuthToken(response: AuthResponse): void {
    this.tokenStorage.setToken(response.token);
    this.tokenStorage.markAuthenticated(true);

    const nextUser = response.user ?? null;
    if (nextUser) {
      this.currentUser$.next(nextUser);
      this.authStateSubject$.next(AuthState.AUTHENTICATED);
      // Track user login to PostHog (non-blocking)
      this.identifyUserIfNeeded(nextUser);
    } else {
      // No user data in response, but we have a valid token
      // Set AUTHENTICATED immediately so guards pass
      this.authStateSubject$.next(AuthState.AUTHENTICATED);
      // Load user details asynchronously in background
      this.loadCurrentUser();
    }
    
    // Schedule proactive token refresh
    this.refreshTokenService.scheduleRefresh(response.token).pipe(take(1)).subscribe({
      error: (err) => {
        console.warn('Failed to schedule token refresh:', err);
        // Non-fatal: token will be re-validated on next request
      }
    });
  }

  /**
   * Check if token exists
   */
  private hasToken(): boolean {
    return this.tokenStorage.hasToken();
  }

  /**
   * Clean up subscriptions and timers
   * Called automatically when service is destroyed
   */
  ngOnDestroy(): void {
    this.refreshTokenService.cancelRefresh();
    if (this.loadCurrentUserSubscription) {
      this.loadCurrentUserSubscription.unsubscribe();
    }
    this.refreshSuccessSubscription?.unsubscribe();
    this.refreshFailureSubscription?.unsubscribe();
  }

  private bindRefreshEvents(): void {
    this.refreshSuccessSubscription?.unsubscribe();
    this.refreshFailureSubscription?.unsubscribe();

    this.refreshSuccessSubscription = this.refreshTokenService.refreshes$()
      .subscribe((response) => {
        this.setAuthToken(response);
      });

    this.refreshFailureSubscription = this.refreshTokenService.refreshFailures$()
      .subscribe((error) => {
        console.warn('Token refresh failed after retries:', error);
        // Fire logout async without blocking
        this.logout().catch(err => console.warn('Logout failed:', err));
      });
  }

  private identifyUserIfNeeded(user: User): void {
    if (!user?.id) {
      return;
    }

    if (this.lastIdentifiedUserId === user.id) {
      return;
    }

    this.lastIdentifiedUserId = user.id;
    try {
      // Only send the stable user ID to PostHog to avoid leaking PII.
      this.posthogService.identifyUser(user.id);
    } catch (error) {
      console.warn('Failed to track user in PostHog:', error);
    }
  }
}
