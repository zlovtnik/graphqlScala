import { Injectable, OnDestroy, PLATFORM_ID, inject } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { BehaviorSubject, Observable } from 'rxjs';

export type ThemeType = 'blue' | 'purple' | 'emerald' | 'amber' | 'system';

@Injectable({
  providedIn: 'root'
})
export class ThemeService implements OnDestroy {
  private readonly THEME_STORAGE_KEY = 'app-theme';
  private currentTheme$ = new BehaviorSubject<ThemeType>(this.getStoredTheme());
  private platformId = inject(PLATFORM_ID);
  private mediaQuery: MediaQueryList | null = null;
  private onSystemThemeChange?: (e: MediaQueryListEvent) => void;
  private readonly DEFAULT_THEME: Exclude<ThemeType, 'system'> = 'blue';

  constructor() {
    // Apply theme only in browser environment
    if (isPlatformBrowser(this.platformId)) {
      this.initSystemThemeDetection();
      this.applyTheme(this.currentTheme$.value);
    }
  }

  /**
   * Get all available themes
   */
  getAvailableThemes(): ThemeType[] {
    return ['blue', 'purple', 'emerald', 'amber', 'system'];
  }

  /**
   * Get current theme as observable
   */
  getCurrentTheme$(): Observable<ThemeType> {
    return this.currentTheme$.asObservable();
  }

  /**
   * Get current theme synchronously
   */
  getCurrentTheme(): ThemeType {
    return this.currentTheme$.value;
  }

  /**
   * Set theme
   */
  setTheme(theme: ThemeType): void {
    this.currentTheme$.next(theme);
    this.applyTheme(theme);
    if (isPlatformBrowser(this.platformId)) {
      localStorage.setItem(this.THEME_STORAGE_KEY, theme);
    }
  }

  /**
   * Initialize system theme detection
   */
  private initSystemThemeDetection(): void {
    if (typeof window === 'undefined' || !window.matchMedia) {
      return;
    }

    this.mediaQuery = window.matchMedia('(prefers-color-scheme: dark)');
    this.onSystemThemeChange = this.handleSystemThemeChange.bind(this);

    // Use feature detection for listener registration (EventTarget API vs legacy addListener)
    if (this.mediaQuery.addEventListener) {
      this.mediaQuery.addEventListener('change', this.onSystemThemeChange);
    } else if (this.mediaQuery.addListener) {
      // Fallback for older browsers
      this.mediaQuery.addListener(this.onSystemThemeChange as any);
    }

    // Apply system theme if current theme is 'system'
    if (this.currentTheme$.value === 'system') {
      this.applyTheme('system');
    }
  }

  /**
   * Handle system theme change
   */
  private handleSystemThemeChange(_event: MediaQueryListEvent): void {
    if (this.currentTheme$.value === 'system') {
      this.applyTheme('system');
    }
  }

  /**
   * Get theme from localStorage or return default
   */
  private getStoredTheme(): ThemeType {
    if (!isPlatformBrowser(this.platformId)) {
      return this.DEFAULT_THEME; // default theme for SSR
    }
    const stored = localStorage.getItem(this.THEME_STORAGE_KEY);
    if (stored && this.getAvailableThemes().includes(stored as ThemeType)) {
      return stored as ThemeType;
    }
    return this.DEFAULT_THEME; // default theme
  }

  /**
   * Apply theme to DOM
   */
  private applyTheme(theme: ThemeType): void {
    // Return early if document or document.documentElement is not available (SSR)
    if (typeof document === 'undefined' || !document.documentElement) {
      return;
    }

    const root = document.documentElement;
    const actualTheme = this.resolveThemeVariant(theme);

    if (actualTheme === 'blue') {
      // Blue is the default theme; remove attribute to use :root styles
      root.removeAttribute('data-theme');
    } else {
      root.setAttribute('data-theme', actualTheme);
    }
  }

  /**
   * Resolve system theme based on OS preference.
   */
  private resolveThemeVariant(theme: ThemeType): string {
    if (theme !== 'system') {
      return theme;
    }

    const prefersDark = this.mediaQuery?.matches ?? false;
    return prefersDark ? 'dark' : this.DEFAULT_THEME;
  }

  /**
   * Cleanup on service destruction - remove media query listener to prevent memory leaks
   */
  ngOnDestroy(): void {
    if (this.mediaQuery && this.onSystemThemeChange) {
      // Use feature detection for listener removal (EventTarget API vs legacy removeListener)
      if (this.mediaQuery.removeEventListener) {
        this.mediaQuery.removeEventListener('change', this.onSystemThemeChange);
      } else if (this.mediaQuery.removeListener) {
        // Fallback for older browsers
        this.mediaQuery.removeListener(this.onSystemThemeChange as any);
      }
    }
  }
}
