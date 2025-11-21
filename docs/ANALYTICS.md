# PostHog Analytics Integration

This document describes the PostHog analytics integration for tracking user behavior and application events.

## Overview

PostHog is configured for development and production environments to track:
- **Page views**: Automatic tracking via Angular Router integration
- **User identification**: User login/logout events with username and email
- **Feature usage**: Entry points to major features (dynamic-crud, user-management, settings)
- **Form submissions**: Data creation/update form success/failure tracking
- **API performance**: Endpoint response times and error rates
- **Client errors**: JavaScript exceptions and application errors

## Configuration

### Environment Setup

#### Development
```bash
# .env (local development)
POSTHOG_KEY=phc_iZMXiVykuSm6uzfC9UHeJ0r6g4xQzes75co6pq7uLdq
POSTHOG_HOST=https://us.i.posthog.com
```

#### Production
```bash
# Set in GitHub Actions or deployment platform
POSTHOG_KEY=${POSTHOG_API_KEY}  # From secrets manager
POSTHOG_HOST=https://us.i.posthog.com
```

### Angular Configuration Files

**`frontend/src/environments/environment.ts`** (Development):
```typescript
posthog: {
  enabled: true,
  key: process.env['NG_APP_POSTHOG_KEY'] || 'phc_iZMXiVykuSm6uzfC9UHeJ0r6g4xQzes75co6pq7uLdq',
  apiHost: process.env['NG_APP_POSTHOG_HOST'] || 'https://us.i.posthog.com',
}
```

**`frontend/src/environments/environment.prod.ts`** (Production):
```typescript
posthog: {
  enabled: true,
  key: process.env['NG_APP_POSTHOG_KEY'] || '',
  apiHost: process.env['NG_APP_POSTHOG_HOST'] || 'https://us.i.posthog.com',
}
```

## PosthogService API

The `PosthogService` provides the following tracking methods:

### User Identification
```typescript
posthogService.identifyUser(userId: string, properties?: Record<string, any>)
```
Called when user logs in. Sets user ID and custom properties (username, email, etc.).

```typescript
posthogService.resetUser()
```
Called when user logs out. Clears user identity.

### Page Tracking
```typescript
posthogService.trackPageView(url: string)
```
Manually track page view (automatic via Router integration).

### Event Tracking
```typescript
posthogService.trackEvent(eventName: string, properties?: Record<string, any>)
```
Generic event capture with optional metadata.

**Examples**:
```typescript
this.posthogService.trackEvent('create_entity', {
  entity_type: 'user',
  source: 'form'
});
```

### Feature Tracking
```typescript
posthogService.trackFeatureUsage(featureName: string, metadata?: Record<string, any>)
```
Track feature entry points. Automatically prefixed with `feature_`.

**Examples**:
```typescript
this.posthogService.trackFeatureUsage('dynamic_crud', {
  table_name: 'users',
  action: 'browse'
});

this.posthogService.trackFeatureUsage('user_management');
this.posthogService.trackFeatureUsage('settings');
```

### Form Tracking
```typescript
posthogService.trackFormSubmission(
  formName: string,
  success: boolean,
  metadata?: Record<string, any>
)
```
Track form submissions with success/failure status.

**Examples**:
```typescript
// On successful form submission
this.posthogService.trackFormSubmission('create_user', true, {
  fields: ['username', 'email', 'role'],
  duration_ms: 1500
});

// On form validation error
this.posthogService.trackFormSubmission('create_user', false, {
  error: 'email_already_exists',
  field: 'email'
});
```

### Error Tracking
```typescript
posthogService.trackError(errorName: string, details?: Record<string, any>)
```
Track application errors and exceptions.

**Examples**:
```typescript
this.posthogService.trackError('graphql_error', {
  endpoint: '/graphql',
  message: 'Failed to fetch users',
  code: 'GRAPHQL_ERROR'
});

this.posthogService.trackError('validation_error', {
  form: 'login',
  fields: ['username', 'password']
});
```

### API Tracking
```typescript
posthogService.trackApiCall(
  endpoint: string,
  method: string,
  duration: number,
  status: number
)
```
Track API/GraphQL call performance.

**Examples**:
```typescript
const start = performance.now();
// ... make API call ...
const duration = performance.now() - start;
this.posthogService.trackApiCall('/graphql', 'POST', duration, 200);
```

### User Properties
```typescript
posthogService.setUserProperties(properties: Record<string, any>)
```
Update user demographic or custom properties.

**Examples**:
```typescript
this.posthogService.setUserProperties({
  subscription_tier: 'premium',
  company: 'ACME Inc',
  team_size: 50,
  onboarded_at: new Date().toISOString()
});
```

## Auto-Tracked Events

### Page Views
Automatically captured via `PosthogService.initPageTracking()` on Angular Router `NavigationEnd` events.

**Example event properties**:
```json
{
  "name": "Page viewed",
  "properties": {
    "$current_url": "http://localhost:4200/admin/users",
    "timestamp": 1695310920
  }
}
```

### Session Recording
Enabled in development to record user interactions, form inputs, and UI changes.

**Production note**: Set `record_by_default: false` and enable selectively per user cohort if needed.

## Integration Points

### AuthService (User Identification)
- `login()` → `posthogService.identifyUser(user.id, {username, email})`
- `logout()` → `posthogService.resetUser()`

### HTTP Error Interceptor
```typescript
// In error-handling.interceptor.ts
this.posthogService.trackError(error.status, {
  endpoint: request.url,
  message: error.message,
  code: error.statusText
});
```

### Feature Components
```typescript
// In dynamic-crud/pages/table-browser.component.ts
ngOnInit() {
  this.posthogService.trackFeatureUsage('dynamic_crud', {
    table_name: this.tableName
  });
}

// In users/pages/user-management.component.ts
ngOnInit() {
  this.posthogService.trackFeatureUsage('user_management');
}

// In settings/pages/preferences.component.ts
ngOnInit() {
  this.posthogService.trackFeatureUsage('settings');
}
```

## Troubleshooting

### PostHog Not Initializing
**Symptom**: No events appear in PostHog dashboard
**Check**:
1. `POSTHOG_KEY` and `POSTHOG_HOST` in `.env`
2. `environment.posthog.enabled === true`
3. Browser console for initialization log: `✅ PostHog initialized`
4. Network tab for requests to PostHog API (`us.i.posthog.com`)

### Events Not Captured
**Symptom**: `trackEvent()` called but no event in dashboard
**Check**:
1. `isPostHogAvailable()` returns true (check window.posthog exists)
2. User is identified (check `posthogService.identifyUser()` called before events)
3. Event name matches PostHog dashboard filters
4. Session recording or autocapture not interfering

### Production Events Missing
**Symptom**: Events work in dev but not production
**Check**:
1. `environment.prod.ts` has valid `POSTHOG_KEY` from GitHub Actions secrets
2. No environment variable override disabling analytics
3. Firewall/CSP not blocking `us.i.posthog.com`
4. Production build includes PosthogService provider in `app.config.ts`

## Best Practices

1. **Always identify users** before tracking events
   ```typescript
   this.posthogService.identifyUser(user.id, {username: user.username});
   this.posthogService.trackEvent('user_created', {...});
   ```

2. **Use feature tracking for major entry points**
   ```typescript
   ngOnInit() {
     this.posthogService.trackFeatureUsage('my_feature');
   }
   ```

3. **Track form success/failure** to measure conversion funnels
   ```typescript
   onSubmit() {
     try {
       await this.service.save(this.form.value);
       this.posthogService.trackFormSubmission('my_form', true);
     } catch (error) {
       this.posthogService.trackFormSubmission('my_form', false, {error: error.message});
     }
   }
   ```

4. **Track API performance** for backend optimization
   ```typescript
   const start = performance.now();
   const result = await this.httpClient.get(url).toPromise();
   this.posthogService.trackApiCall(url, 'GET', performance.now() - start, 200);
   ```

5. **Set user properties on profile changes**
   ```typescript
   onPreferencesChange(preferences: Preferences) {
     this.posthogService.setUserProperties({
       theme: preferences.theme,
       language: preferences.language,
       notifications_enabled: preferences.enableNotifications
     });
   }
   ```

6. **Reset user on logout**
   ```typescript
   async logout() {
     await this.authService.logout();
     // Already called in authService, but for clarity:
     this.posthogService.resetUser();
   }
   ```

## Dashboard Setup

### Key Metrics to Track

1. **User Adoption**
   - Daily active users (DAU)
   - Monthly active users (MAU)
   - New user registrations

2. **Feature Usage**
   - Dynamic CRUD table browser usage
   - User management feature access
   - Settings/preferences updates

3. **Conversion Funnels**
   - Login → form submission → entity create
   - Settings access → preference change → save

4. **Error Rates**
   - GraphQL errors per endpoint
   - Validation errors per form
   - API error rate by status code

5. **Performance**
   - Page load time
   - API response time percentiles (p50, p95, p99)
   - Form submission duration

## Disabling Analytics

To disable PostHog in specific environments:

```typescript
// environment.ts
posthog: {
  enabled: false,  // Set to false
  key: '',
  apiHost: ''
}
```

**Note**: Disabling analytics in production requires careful review by product/analytics team as it impacts business intelligence.
