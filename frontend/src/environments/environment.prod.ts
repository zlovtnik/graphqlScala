// Production environment configuration
// These values should be replaced with actual production endpoints
// Consider using environment variables or build-time replacements for security
export const environment = {
  production: true,
  graphqlEndpoint: process.env['GRAPHQL_ENDPOINT'] || 'https://your-production-domain.com/graphql',
  apiUrl: process.env['API_URL'] || 'https://your-production-domain.com',
  enableHydration: true,
  posthog: {
    enabled: true,
    key: process.env['NG_APP_POSTHOG_KEY'] || '',
    apiHost: process.env['NG_APP_POSTHOG_HOST'] || 'https://us.i.posthog.com',
  },
};
