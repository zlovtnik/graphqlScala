import { Injectable, inject, OnDestroy } from '@angular/core';
import { Router, NavigationEnd } from '@angular/router';
import { filter } from 'rxjs/operators';
import { Subscription } from 'rxjs';
import posthog from 'posthog-js';

/**
 * PostHog Analytics Service
 * Tracks user behavior, page views, custom events, and user properties
 * Integrated with Angular routing for automatic page view tracking
 */
@Injectable({
  providedIn: 'root',
})
export class PosthogService implements OnDestroy {
  private router = inject(Router);
  private routerSub: Subscription | null = null;

  constructor() {
    this.initPageTracking();
  }

  /**
   * Initialize automatic page view tracking on route changes
   */
  private initPageTracking(): void {
    if (!this.isPostHogAvailable()) {
      return;
    }
    this.routerSub = this.router.events
      .pipe(filter((event) => event instanceof NavigationEnd))
      .subscribe((event: NavigationEnd) => {
        // Track page view with route name
        this.trackPageView(event.urlAfterRedirects);
      });
  }

  /**
   * Track a page view
   * @param url The page URL or route path
   */
  trackPageView(url: string): void {
    if (!this.isPostHogAvailable()) {
      return;
    }
    try {
      posthog.capture('page_view', {
        page_path: url,
        page_title: document.title,
      });
    } catch (error) {
      // Silently ignore PostHog errors
    }
  }

  /**
   * Track a custom event
   * @param event Event name (e.g., 'button_clicked', 'form_submitted')
   * @param properties Optional event properties
   */
  trackEvent(event: string, properties?: Record<string, any>): void {
    if (!this.isPostHogAvailable()) {
      return;
    }
    try {
      posthog.capture(event, properties || {});
    } catch (error) {
      // Silently ignore PostHog errors
    }
  }

  /**
   * Track user login
   * @param userId Unique user identifier
   * @param userProperties Optional user properties (email, username, etc.)
   */
  identifyUser(userId: string, userProperties?: Record<string, any>): void {
    if (!this.isPostHogAvailable()) {
      return;
    }
    try {
      posthog.identify(userId, {
        userId,
        ...userProperties,
      });
    } catch (error) {
      // Silently ignore PostHog errors
    }
  }

  /**
   * Track user logout / reset identity
   */
  resetUser(): void {
    if (!this.isPostHogAvailable()) {
      return;
    }
    try {
      posthog.reset();
    } catch (error) {
      // Silently ignore PostHog errors
    }
  }

  /**
   * Set user properties for the current user
   * @param properties Object with user properties
   */
  setUserProperties(properties: Record<string, any>): void {
    if (!this.isPostHogAvailable()) {
      return;
    }
    try {
      posthog.people.set(properties);
    } catch (error) {
      // Silently ignore PostHog errors
    }
  }

  /**
   * Track feature usage
   * @param featureName Name of the feature (e.g., 'dynamic_crud', 'user_management')
   * @param metadata Optional metadata about feature usage
   */
  trackFeatureUsage(
    featureName: string,
    metadata?: Record<string, any>
  ): void {
    this.trackEvent('feature_used', {
      feature: featureName,
      ...metadata,
    });
  }

  /**
   * Track errors for monitoring
   * @param errorName Name/type of error
   * @param errorDetails Error details
   */
  trackError(errorName: string, errorDetails?: Record<string, any>): void {
    this.trackEvent('error_occurred', {
      error_name: errorName,
      ...errorDetails,
    });
  }

  /**
   * Track form submission
   * @param formName Name of the form
   * @param success Whether submission succeeded
   * @param metadata Optional metadata
   */
  trackFormSubmission(
    formName: string,
    success: boolean,
    metadata?: Record<string, any>
  ): void {
    this.trackEvent('form_submitted', {
      form_name: formName,
      success,
      ...metadata,
    });
  }

  /**
   * Track API call for performance monitoring
   * @param endpoint API endpoint
   * @param method HTTP method
   * @param duration Duration in milliseconds
   * @param status HTTP status code
   */
  trackApiCall(
    endpoint: string,
    method: string,
    duration: number,
    status: number
  ): void {
    this.trackEvent('api_call', {
      endpoint,
      method,
      duration_ms: duration,
      status_code: status,
    });
  }

  /**
   * Get distinct ID of current user/session
   * @returns Distinct ID or undefined if PostHog not available
   */
  getDistinctId(): string | undefined {
    if (!this.isPostHogAvailable()) {
      return undefined;
    }
    try {
      return posthog.get_distinct_id();
    } catch (error) {
      return undefined;
    }
  }

  /**
   * Check if PostHog is initialized and available
   */
  private isPostHogAvailable(): boolean {
    try {
      return typeof posthog !== 'undefined' && !!(posthog as any).config;
    } catch {
      return false;
    }
  }

  /**
   * Clean up subscriptions on service destruction
   */
  ngOnDestroy(): void {
    this.routerSub?.unsubscribe();
  }
}
