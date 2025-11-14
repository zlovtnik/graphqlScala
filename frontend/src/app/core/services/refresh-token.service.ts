import { Injectable, inject } from '@angular/core';
import { Apollo } from 'apollo-angular';
import { Observable, of, throwError, Subject, Subscription, timer } from 'rxjs';
import { catchError, map, take, retry } from 'rxjs/operators';
import { AuthResponse } from './auth.service';
import { REFRESH_TOKEN_MUTATION } from '../graphql';

/**
 * Refresh Token Service
 *
 * Handles proactive token refresh to prevent session expiration.
 *
 * ARCHITECTURE:
 * 1. Server returns tokens with exp claim in JWT payload
 * 2. Client calculates refresh time: (exp - now) * 0.8 (refresh at 80% of lifetime)
 * 3. When token approaches expiry, client calls /refresh endpoint
 * 4. Server validates refresh token and returns new access token
 * 5. Token stored in httpOnly cookie (automatic with Set-Cookie header)
 *
 * BENEFITS:
 * - No race conditions with manual token validation
 * - Seamless refresh without user interaction
 * - Server-side expiration control
 * - Client never stores tokens directly (production)
 */

export interface RefreshResponse {
  refreshToken: AuthResponse;
}

@Injectable({
  providedIn: 'root',
})
export class RefreshTokenService {
  private refreshTimer?: number;
  private activeRefreshSubscription?: Subscription;
  private readonly apollo = inject(Apollo);
  private readonly refreshSubject = new Subject<AuthResponse>();
  private readonly refreshFailureSubject = new Subject<unknown>();

  private readonly REFRESH_FACTOR = 0.8;
  private readonly SAFETY_MARGIN_MS = 5000;
  private readonly MIN_REFRESH_INTERVAL_MS = 1000;
  private readonly MAX_REFRESH_RETRIES = 2;
  private readonly RETRY_DELAY_MS = 1000;

  refreshes$(): Observable<AuthResponse> {
    return this.refreshSubject.asObservable();
  }

  refreshFailures$(): Observable<unknown> {
    return this.refreshFailureSubject.asObservable();
  }

  /**
   * Schedule a proactive token refresh based on JWT expiration
   *
   * Parses the JWT token to extract the exp claim and schedules
   * a refresh at 80% of the token's lifetime.
   *
   * @param token JWT access token
   * @returns Observable that completes when refresh is scheduled
   */
  scheduleRefresh(token: string): Observable<void> {
    try {
      if (typeof window === 'undefined') {
        return of(undefined);
      }

      const delay = this.calculateNextDelay(token);
      if (delay === null) {
        return of(undefined);
      }

      this.registerRefreshTimer(token, delay);

      return of(undefined);
    } catch (error) {
      console.error('Failed to schedule token refresh:', error);
      return throwError(() => error);
    }
  }

  /**
   * Perform token refresh immediately
   *
   * Calls the GraphQL refresh endpoint to get a new token.
   * Server will set new token as httpOnly cookie.
   *
   * @returns Observable of refresh response
   */
  performRefresh(): Observable<AuthResponse> {
    return this.apollo
      .mutate<RefreshResponse>({
        mutation: REFRESH_TOKEN_MUTATION,
      })
      .pipe(
        map((result) => {
          if (!result.data?.refreshToken) {
            throw new Error('Invalid refresh response from server');
          }
          return result.data.refreshToken;
        }),
        take(1),
        catchError((error) => {
          console.error('Token refresh failed:', error);
          return throwError(() => error);
        })
      );
  }

  /**
   * Extract expiry time from JWT token
   *
   * Parses the JWT payload (without verification - done server-side)
   * and extracts the exp claim.
   *
   * @param token JWT token
   * @returns Expiry time in milliseconds since epoch, or null if invalid
   */
  private extractTokenExpiry(token: string): number | null {
    try {
      const parts = token.split('.');
      if (parts.length !== 3) {
        return null;
      }

      // Decode base64url payload
      const payload = JSON.parse(
        atob(parts[1].replace(/-/g, '+').replace(/_/g, '/'))
      );

      if (!payload.exp) {
        return null;
      }

      // exp is in seconds, convert to milliseconds
      return payload.exp * 1000;
    } catch (error) {
      console.warn('Failed to parse token expiry:', error);
      return null;
    }
  }

  /**
   * Cancel scheduled refresh
   * Called on logout or when token is invalidated
   */
  cancelRefresh(): void {
    this.clearExistingTimer();
    if (this.activeRefreshSubscription) {
      this.activeRefreshSubscription.unsubscribe();
      this.activeRefreshSubscription = undefined;
    }
  }

  private registerRefreshTimer(token: string, delay: number): void {
    this.clearExistingTimer();

    if (delay <= 0) {
      this.startRefreshAttempt();
      return;
    }

    this.refreshTimer = window.setTimeout(() => {
      this.startRefreshAttempt();
    }, delay);
  }

  private startRefreshAttempt(): void {
    this.clearExistingTimer();
    this.activeRefreshSubscription?.unsubscribe();

    this.activeRefreshSubscription = this.performRefresh()
      .pipe(
        retry({
          count: this.MAX_REFRESH_RETRIES,
          delay: (_, retryCount) => timer(this.RETRY_DELAY_MS * retryCount),
        })
      )
      .subscribe({
        next: (response) => {
          this.refreshSubject.next(response);
          const nextDelay = this.calculateNextDelay(response.token);
          if (nextDelay !== null) {
            this.registerRefreshTimer(response.token, nextDelay);
          } else {
            this.clearExistingTimer();
          }
        },
        error: (err) => {
          console.warn('Token refresh failed after retries:', err);
          this.refreshFailureSubject.next(err);
          this.cancelRefresh();
        },
      });
  }

  private calculateNextDelay(token: string): number | null {
    const expiryTime = this.extractTokenExpiry(token);
    if (expiryTime === null) {
      return null;
    }

    const now = Date.now();
    const timeUntilExpiry = expiryTime - now;

    if (timeUntilExpiry <= 0) {
      return 0;
    }

    const safetyWindow = Math.max(timeUntilExpiry - this.SAFETY_MARGIN_MS, 0);
    const targetDelay = Math.floor(timeUntilExpiry * this.REFRESH_FACTOR);
    const scheduledDelay = Math.min(targetDelay, safetyWindow);

    if (scheduledDelay <= 0) {
      return 0;
    }

    return Math.max(scheduledDelay, this.MIN_REFRESH_INTERVAL_MS);
  }

  private clearExistingTimer(): void {
    if (this.refreshTimer !== undefined) {
      clearTimeout(this.refreshTimer);
      this.refreshTimer = undefined;
    }
  }
}
