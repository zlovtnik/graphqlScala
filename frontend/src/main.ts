import { bootstrapApplication } from '@angular/platform-browser';
import { appConfig } from './app/app.config';
import { AppComponent } from './app/app.component';
import posthog from 'posthog-js';
import { environment } from './environments/environment';

// Initialize PostHog for product analytics
if (environment.posthog.enabled && environment.posthog.key) {
  try {
    posthog.init(environment.posthog.key, {
      api_host: environment.posthog.apiHost,
      person_profiles: 'identified_only',
      session_recording: {
        recordCrossOriginIframes: false,
      },
      loaded: (posthogInstance) => {
        console.log('✅ PostHog initialized', {
          key: environment.posthog.key?.substring(0, 10) + '...',
          host: environment.posthog.apiHost,
        });
      },
    });
    // Make PostHog globally accessible
    (window as any).posthog = posthog;
  } catch (error) {
    console.warn('PostHog initialization failed (likely blocked by ad blocker):', error);
  }
}

bootstrapApplication(AppComponent, appConfig)
  .catch((err) => console.error(err));
