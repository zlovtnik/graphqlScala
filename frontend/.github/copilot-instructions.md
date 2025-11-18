# Copilot Instructions

## Project Snapshot
- Angular 18 standalone app + NG-ZORRO UI + Apollo GraphQL client; backend expected at `https://localhost:8443/graphql` (see `src/environments`).
- Project root scripts: `npm start`, `npm run build`, `npm test`, `npm run serve:ssr:frontend`, `npm run codegen*`, `npm run lint[:fix]`.
- SSR build artifacts land in `dist/frontend/{browser,server}`; Express entrypoint is `server.ts`.

## Build & Run Workflow
- Install with `npm install` (Node 18–21). `npm start` runs `ng serve` at :4200.
- `npm run build` triggers the Angular production builder; pair it with `npm run serve:ssr:frontend` to verify the server bundle.
- Karma tests run via `npm test`; add focused specs under `src/app/**/*.spec.ts`.
- GraphQL typings use `npm run codegen` once `codegen.yml` exists—today the canonical sources are the `.ts` files in `src/app/core/graphql`.

## Architecture & Conventions
- Routing lives in `src/app/app.routes.ts`; every route lazy-loads a standalone component with optional guards. Add new pages the same way instead of creating NgModules.
- Global providers are declared in `src/app/app.config.ts` (router, Apollo, HTTP, NG-ZORRO locale/icons, service worker). Extend this file when wiring new platform services.
- `src/app/core` hosts infrastructure: `graphql/` operations, `services/` for stateful singletons, `guards/`, and interceptors.
- `src/app/features/**` contains user-facing pages grouped by domain (auth, dynamic-crud, users). `src/app/shared` exposes reusable UI (notification center, placeholder, shortcuts modal).
- Placeholder routes use `shared/components/placeholder.component.ts`; reuse it for in-progress sections instead of inventing new stubs.

## Auth & GraphQL Flow
- `graphql.config.ts` wires Apollo with an `HttpLink` pointed at the environment endpoint and a context link that injects `Authorization` headers from `localStorage['auth_token']` only when `window` exists (SSR safe). Reuse the `setContext` pattern for new middleware.
- Operations live in `src/app/core/graphql/*.ts` using `gql` template literals; `index.ts` re-exports them. Keep the twin `.graphql` files in sync—they are documentation-only until `codegen.yml` is introduced.
- `AuthService` orchestrates login/register/current-user fetch, exposes `AuthState` (`LOADING`, `AUTHENTICATED`, `UNAUTHENTICATED`), and calls `RefreshTokenService` to schedule renewals at ~80% of the JWT lifetime.
- `TokenStorageAdapter` abstracts storage: localStorage for dev, httpOnly cookies in prod while mirroring auth state in `sessionStorage`. Never read tokens directly outside this adapter.
- `authGuard` waits for `AuthService` to finish `loadCurrentUser()` before routing; tap into `getAuthState$()` for reliable gating instead of checking booleans.

## UI, Theme, and PWA Details
- NG-ZORRO icons must be registered in `src/app/icons-provider.ts`; import there to avoid bundling unused assets.
- Theme switching relies on CSS custom properties in `src/styles.css`/`theme.less` and `ThemeService`, which toggles a `data-theme` attribute. Guard all DOM access with `isPlatformBrowser` like the service does.
- `NotificationService`, `ModalService`, and `KeyboardService` live under `core/services` and are already hooked into shared components (e.g., notification center); reuse them rather than duplicating logic.
- `PwaService` captures `beforeinstallprompt` events and exposes `installPromptVisible$`; consume that observable when adding install banners.

## SSR, Security, and Ops
- The Express host in `server.ts` enforces host header validation, rate limiting (configurable via `RATE_LIMIT_MAX`), proxy trust (`TRUST_PROXY`), and 30s SSR timeouts. Preserve these guards when touching server middleware.
- Production envs derive `graphqlEndpoint`/`apiUrl` from `process.env` (see `src/environments/environment.prod.ts`); never hardcode secrets in client code.
- When writing browser-only logic, follow existing guards (`typeof window === 'undefined'`, `isPlatformBrowser`) to keep SSR renders safe.

## Contribution Tips
- Follow RxJS patterns already in services (`pipe(filter(...), take(1))`) for deterministic auth flows.
- Prefer barrel exports (e.g., `core/graphql/index.ts`) for new operations so lazy-loaded components can import from a single module.
- Keep unit tests colocated (`*.spec.ts`), mirroring existing specs like `core/services/notification.service.spec.ts`.
- After UI changes, verify responsive behavior in `main.component` and `features/auth` since those routes are the primary entry points.
