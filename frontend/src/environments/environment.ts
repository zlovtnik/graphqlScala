export const environment = {
  production: false,
  // For local development: Use HTTP for development server. For HTTPS, run:
  //   mkcert -install
  //   mkcert localhost 127.0.0.1 ::1
  //   ng serve --ssl --ssl-cert mkcert-localhost.pem --ssl-key mkcert-localhost-key.pem
  // Then update endpoints below to https://localhost:4200
  graphqlEndpoint: 'http://localhost:8080/graphql',
  apiUrl: 'http://localhost:8080',
  enableHydration: false
};
