# SSF GraphQL Copilot Instructions

## Architecture & Flow
- Spring Boot 3.5 GraphQL gateway lives under `src/main/java/com/rcs/ssf`, fronts Oracle stored procs, MinIO, and Redis, and serves Jetty TLS on 8443.
- Requests ride `JwtAuthenticationFilter` → `SecurityConfig` filter chain → `GraphQLAuthorizationInstrumentation` enforcing JWT plus field-level auth.

## Code Organization
- GraphQL schema is `src/main/resources/graphql/schema.graphqls`; resolvers stay thin and delegate to `service/**` + `repository/**` for business rules.
- SQL assets ship from `sql/**` while Flyway migrations live in `db/migration` using `V###__description.sql`; keep `sql/master.sql` ordering in sync.
- Angular 18 client in `frontend/` (Apollo in `src/app/core/graphql`) must be updated alongside schema changes and regenerated via `npm run codegen`.

## Data & Services
- DTOs in `dto/**` own Jakarta validation so controllers/resolvers only orchestrate.
- Stored procedure access is wrapped through repository beans plus dynamic CRUD helpers from `sql/packages/**`; use `@Transactional` conservatively for multi-entity writes.
- Partition automation scripts (`scripts/partition-maintenance.sh`, `infra/cronjobs/**`) assume Oracle creds in `.secrets/oracle-password` with chmod 600.

## Security & Environment
- Never add unauthenticated HTTP paths; update `SecurityConfig` + instrumentation when touching auth.
- Startup validates `JWT_SECRET`, `MINIO_ACCESS_KEY`, and `MINIO_SECRET_KEY` via `EnvironmentValidator` and `JwtProperties.validateSecretEntropy`; document any new env var in `README.md` + `HELP.md`.

## Caching, Resilience, Observability
- Default cache stack: Caffeine via `CacheConfig` with Redis secondary; reuse existing cache names and keep persisted query hashes stable for APQ stored in Redis.
- Circuit breakers/retries configured in `Resilience4jConfig` (`database`, `redis`, `minio`, `auth-service`, `audit-service`); export new metrics through Micrometer so Grafana dashboards stay aligned.
- Health contributors live in `HealthConfig`, pool monitoring under `PoolMonitoringConfig`, MinIO wiring inside `MinioConfig`; extend these instead of rolling bespoke checks.

## Build, Test, Runtime
- `./gradlew clean build` enforces JaCoCo ≥75%; `./gradlew test` auto-runs `jacocoTestReport` with HTML under `build/jacocoHtml`.
- Use JUnit 5 + Mockito + `spring-graphql-test`; prefer `@GraphQlTest` and `WebGraphQlTester` slices, spinning up Testcontainers only when Oracle behavior is required.
- Runtime via `./gradlew bootRun` expects Redis 7+, Oracle, MinIO (see `docker-compose.yml` for local stack); HTTPS cert is `src/main/resources/keystore.p12`.
- Container image builds use `./gradlew bootBuildImage --imageName=ssf-graphql:latest`; keep Dockerfile, `docker-compose.yml`, and env var docs in sync.

## Frontend & Docs
- Backend schema changes require matching Angular updates plus doc snippets in `frontend/README.md`; keep dev server pointing at `http://localhost:8080/graphql` unless updating env files.
- Any change to auth, persistence, or migrations mandates doc updates (`README.md`, `HELP.md`, `sql/README.md`) and both happy-path and failure-path tests.
- TLS/port changes must be reflected across `nginx.conf`, Dockerfile, and `frontend/ngsw-config.json` so the PWA continues to load cached assets.

## CI Expectations
- GitHub Actions rely on Gradle wrapper + npm CI; never delete `gradlew`/`gradlew.bat` and update `static-analysis.datadog.yml` when adding modules to keep scans green.
