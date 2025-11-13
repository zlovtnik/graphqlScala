import { Injectable, PLATFORM_ID, inject } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { BehaviorSubject, Observable } from 'rxjs';

export type ThemeType = 'blue' | 'purple' | 'emerald' | 'amber' | 'system';

@Injectable({
  providedIn: 'root'
})
export class ThemeService {
  private readonly THEME_STORAGE_KEY = 'app-theme';
  private currentTheme$ = new BehaviorSubject<ThemeType>(this.getStoredTheme());
  private platformId = inject(PLATFORM_ID);
  private mediaQuery: MediaQueryList | null = null;

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
    this.mediaQuery.addEventListener('change', this.handleSystemThemeChange.bind(this));

    // Apply system theme if current theme is 'system'
    if (this.currentTheme$.value === 'system') {
      this.applyTheme('system');
    }
  }

  /**
   * Handle system theme change
   */
  private handleSystemThemeChange(e: MediaQueryListEvent): void {
    if (this.currentTheme$.value === 'system') {
      this.applyTheme('system');
    }
  }

  /**
   * Get theme from localStorage or return default
   */
  private getStoredTheme(): ThemeType {
    if (!isPlatformBrowser(this.platformId)) {
      return 'blue'; // default theme for SSR
    }
    const stored = localStorage.getItem(this.THEME_STORAGE_KEY);
    if (stored && this.getAvailableThemes().includes(stored as ThemeType)) {
      return stored as ThemeType;
    }
    return 'blue'; // default theme
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
    let actualTheme: string;

    if (theme === 'system') {
      actualTheme = 'blue'; // TODO: implement dark themes
    } else {
      actualTheme = theme;
    }

    if (actualTheme === 'blue') {
      // Blue is the default theme; remove attribute to use :root styles
      root.removeAttribute('data-theme');
    } else {
      root.setAttribute('data-theme', actualTheme);
    }
  }
}
