import { Injectable, PLATFORM_ID, OnDestroy, inject } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { Apollo } from 'apollo-angular';
import { BehaviorSubject, Observable, of, Subscription } from 'rxjs';
import { map, tap, catchError, take } from 'rxjs/operators';
import {
  LOGIN_MUTATION,
  REGISTER_MUTATION,
  GET_CURRENT_USER_QUERY
} from '../graphql';
import { TokenStorageAdapter } from './token-storage.adapter';
import { RefreshTokenService } from './refresh-token.service';

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
  user: User;
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

  private apollo = inject(Apollo);
  private tokenStorage = inject(TokenStorageAdapter);
  private refreshTokenService = inject(RefreshTokenService);
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
        if (!result.data?.login) {
          throw new Error('Invalid login response from server');
        }
        return result.data.login;
      }),
      tap(response => this.setAuthToken(response))
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
        if (!result.data?.register) {
          throw new Error('Invalid register response from server');
        }
        return result.data.register;
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
          this.authStateSubject$.next(AuthState.AUTHENTICATED);
        } else {
          // No user in response - invalid token
          this.authStateSubject$.next(AuthState.UNAUTHENTICATED);
        }
      }),
      catchError((error) => {
        // Query failed - token is invalid or expired
        console.error('Failed to load current user:', error);
        this.authStateSubject$.next(AuthState.UNAUTHENTICATED);
        this.tokenStorage.clearToken();
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
    
    // Schedule proactive token refresh
    this.refreshTokenService.scheduleRefresh(response.token).pipe(take(1)).subscribe({
      error: (err) => {
        console.warn('Failed to schedule token refresh:', err);
        // Non-fatal: token will be re-validated on next request
      }
    });

    this.currentUser$.next(response.user);
    this.authStateSubject$.next(AuthState.AUTHENTICATED);
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
        void this.logout();
      });
  }
}
