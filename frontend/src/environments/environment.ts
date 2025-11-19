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
};
