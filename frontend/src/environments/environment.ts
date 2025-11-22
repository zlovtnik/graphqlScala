const readNgEnv = (key: string, fallback = ''): string => {
  const globalEnv = (globalThis as any)?.__env;
  if (globalEnv && typeof globalEnv[key] === 'string') {
    return globalEnv[key] as string;
  }

  const processEnv = (globalThis as any)?.process?.env;
  if (processEnv && typeof processEnv[key] === 'string') {
    return processEnv[key] as string;
  }

  return fallback;
};

export const environment = {
  production: false,
  // BACKEND ENDPOINTS: The graphqlEndpoint and apiUrl below point to the backend service on port 8443.
  // Ensure the backend is running on https://localhost:8443 with valid TLS certificates.
  //
  // FRONTEND HTTPS (optional): To serve the Angular dev server over HTTPS (port 4200):
  //   1. Generate local dev certs: mkcert localhost 127.0.0.1 ::1
  //   2. Run: ng serve --ssl --ssl-cert mkcert-localhost.pem --ssl-key mkcert-localhost-key.pem
  //   3. Frontend will be accessible at https://localhost:4200
  //   Note: This is independent of the backend endpoints. Leave graphqlEndpoint and apiUrl as-is
  //   to point to the backend service; the frontend runs on a different port/protocol.
  graphqlEndpoint: 'https://localhost:8443/graphql',
  apiUrl: 'https://localhost:8443',
  enableHydration: false,
  posthog: (() => {
    const isProduction = typeof window !== 'undefined' && window.location.hostname === 'prod.example.com';
    const posthogKey = readNgEnv('NG_APP_POSTHOG_KEY');
    // Use dev fallback key only when not in production
    const key = posthogKey || (isProduction ? '' : 'local-dev-posthog-key');
    const enabled = !!(key && key.length > 0);
    if (!enabled) {
      console.warn('PostHog disabled: API key not configured. Set NG_APP_POSTHOG_KEY environment variable to enable analytics.');
    }
    return {
      enabled,
      key,
      apiHost: readNgEnv('NG_APP_POSTHOG_HOST', 'https://us.i.posthog.com'),
    };
  })(),
};
