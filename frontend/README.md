# SSF GraphQL Frontend

Angular 18 + NG-ZORRO + Apollo client application targeting the SSF GraphQL backend.

## Prerequisites

- Node.js 18+ (currently using v25 for development)
- npm (bundled with Node.js)
- Java backend running at `http://localhost:8080/graphql`

## Install dependencies

```bash
npm install
```

## Key scripts

```bash
npm start            # Run Angular dev server at http://localhost:4200
npm run codegen      # Generate GraphQL typings
npm run codegen:watch # Watch GraphQL documents and regenerate
npm run build        # Production build artifacts in dist/
npm test             # Run unit tests
```

## Project structure

```
src/
  app/
    core/           # Services, guards, interceptors
    shared/         # Reusable components and models
    graphql/        # GraphQL config, operations, generated types
    features/       # Feature areas (e.g., users)
```

## Apollo client configuration

Defined in `src/app/graphql.config.ts` with auth token support via `localStorage['auth_token']` and environment-driven endpoint URLs (`src/environments`).

## Code generation

Configure operations under `src/app/graphql`. Schema is read from `http://localhost:8080/graphql`. Generated types are written to `src/app/graphql/generated.ts`.
