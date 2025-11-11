import { Injectable, inject } from '@angular/core';
import { Apollo } from 'apollo-angular';
import { Observable, of, throwError } from 'rxjs';
import { catchError, map, take } from 'rxjs/operators';
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
  providedIn: 'root'
})
export class RefreshTokenService {
  private refreshTimer: NodeJS.Timeout | undefined = undefined;
  private apollo = inject(Apollo);

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
      const expiryTime = this.extractTokenExpiry(token);
      if (!expiryTime) {
        return of(undefined);
      }

      const now = Date.now();
      const timeUntilExpiry = expiryTime - now;

      if (timeUntilExpiry <= 0) {
        // Token already expired
        return of(undefined);
      }

      // Schedule refresh at 80% of token lifetime
      const refreshTime = Math.floor(timeUntilExpiry * 0.8);

      // Clear any existing timer
      if (this.refreshTimer) {
        clearTimeout(this.refreshTimer);
      }

      this.refreshTimer = setTimeout(() => {
        this.performRefresh().subscribe({
          error: (err) => {
            console.warn('Token refresh failed:', err);
            // Token will be re-validated on next request
          }
        });
      }, refreshTime);

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
    return this.apollo.mutate<RefreshResponse>({
      mutation: REFRESH_TOKEN_MUTATION
    }).pipe(
      map(result => {
        if (!result.data?.refreshToken) {
          throw new Error('Invalid refresh response from server');
        }
        return result.data.refreshToken;
      }),
      take(1),
      catchError(error => {
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
    if (this.refreshTimer) {
      clearTimeout(this.refreshTimer);
      this.refreshTimer = undefined;
    }
  }
}
