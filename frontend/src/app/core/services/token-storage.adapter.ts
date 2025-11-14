import { Injectable, PLATFORM_ID, inject } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { environment } from '../../../environments/environment';

/**
 * Token Storage Adapter
 * 
 * Provides a secure abstraction for token storage that supports both development
 * (localStorage) and production (httpOnly cookies) environments.
 * 
 * SECURITY NOTES:
 * - Development: Uses localStorage for convenience (XSS-vulnerable but acceptable for dev)
 * - Production: Relies on httpOnly, Secure, SameSite cookies (not readable from JS)
 * - Tokens are never directly readable by JavaScript code in production
 * - Server-side manages token expiration via cookie attributes
 * - Client proactively refreshes tokens via /refresh endpoint before expiry
 * 
 * MIGRATION STRATEGY:
 * 1. Server sets tokens in httpOnly cookies with appropriate flags
 * 2. Client uses this adapter for consistency across environments
 * 3. Apollo Client automatically includes cookies in GraphQL requests
 * 4. Client calls /refresh endpoint proactively before token expiry
 * 5. Remove direct localStorage token access after server update
 */
@Injectable({
  providedIn: 'root'
})
export class TokenStorageAdapter {
  private readonly TOKEN_STORAGE_KEY = 'auth-token';
  private readonly AUTH_STATE_KEY = 'auth-state';
  private isDevelopment = !environment.production;
  private platformId = inject(PLATFORM_ID);

  /**
   * Get stored access token
   * 
   * IMPORTANT: In production, this returns null because the token is stored
   * in an httpOnly cookie that's not readable from JavaScript.
   * The server will automatically include the cookie in requests.
   * 
   * In development, returns the localStorage value for convenience.
   * 
   * @returns Token string or null if not available
   */
  getToken(): string | null {
    if (!isPlatformBrowser(this.platformId)) {
      return null;
    }

    // Development: Read from localStorage
    if (this.isDevelopment) {
      return localStorage.getItem(this.TOKEN_STORAGE_KEY);
    }

    // Production: Token stored in httpOnly cookie, not readable from JS
    // Apollo Client will automatically include cookies in requests
    return null;
  }

  /**
   * Store access token
   * 
   * IMPORTANT: In production, the server sets the token as an httpOnly cookie.
   * This method is provided for development convenience only.
   * 
   * @param token Access token to store
   */
  setToken(token: string): void {
    if (!isPlatformBrowser(this.platformId)) {
      return;
    }

    if (this.isDevelopment) {
      localStorage.setItem(this.TOKEN_STORAGE_KEY, token);
    }
    this.setAuthFlag(true);
    // Production: Server handles cookie storage via Set-Cookie header
  }

  /**
   * Check if token exists
   * 
   * @returns True if token is available
   */
  hasToken(): boolean {
    if (!isPlatformBrowser(this.platformId)) {
      return false;
    }

    if (this.isDevelopment) {
      return !!localStorage.getItem(this.TOKEN_STORAGE_KEY);
    }

    return this.getAuthFlag();
  }

  /**
   * Clear stored token
   */
  clearToken(): void {
    if (!isPlatformBrowser(this.platformId)) {
      return;
    }

    if (this.isDevelopment) {
      localStorage.removeItem(this.TOKEN_STORAGE_KEY);
    }
    this.setAuthFlag(false);
    // Production: Server clears httpOnly cookie via Set-Cookie with Max-Age=0
  }

  /**
   * Explicitly mark authentication state when using httpOnly cookies.
   * Helps distinguish between authenticated sessions and anonymous users.
   */
  markAuthenticated(authenticated: boolean): void {
    if (!isPlatformBrowser(this.platformId)) {
      return;
    }
    this.setAuthFlag(authenticated);
  }

  private setAuthFlag(enabled: boolean): void {
    try {
      sessionStorage.setItem(this.AUTH_STATE_KEY, enabled ? 'true' : 'false');
    } catch (error) {
      console.warn('Unable to persist authentication state flag', error);
    }
  }

  private getAuthFlag(): boolean {
    try {
      return sessionStorage.getItem(this.AUTH_STATE_KEY) === 'true';
    } catch (error) {
      console.warn('Unable to read authentication state flag', error);
      return false;
    }
  }
}
