<div align="center">
  <h1>✨ SSF GraphQL Platform</h1>
  <p>Secure, cloud-ready GraphQL APIs powered by Spring Boot 3, JWT, Oracle Database, and MinIO object storage.</p>
  <p>
    <a href="https://openjdk.org/projects/jdk/21/">
      <img alt="Java" src="https://img.shields.io/badge/Java-21-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white" />
    </a>
    <a href="https://spring.io/projects/spring-boot">
      <img alt="Spring Boot" src="https://img.shields.io/badge/Spring%20Boot-3.4.5-6DB33F?style=for-the-badge&logo=springboot&logoColor=white" />
    </a>
    <a href="https://graphql.org/">
      <img alt="GraphQL" src="https://img.shields.io/badge/GraphQL-Query%20First-E10098?style=for-the-badge&logo=graphql&logoColor=white" />
    </a>
    <a href="https://www.oracle.com/database/technologies/appdev/xe.html">
      <img alt="Oracle Database" src="https://img.shields.io/badge/Oracle%20DB-Enterprise-red?style=for-the-badge&logo=oracle&logoColor=white" />
    </a>
    <a href="https://min.io/">
      <img alt="MinIO" src="https://img.shields.io/badge/MinIO-Object%20Storage-C72E49?style=for-the-badge&logo=min.io&logoColor=white" />
    </a>
  </p>
</div>

> **SSF** (Secure Services Framework) delivers authentication-first GraphQL endpoints with auditable login flows, hardened JWT validation, and production-friendly observability baked in.

---

## 📚 Table of Contents
- [Overview](#overview)
- [Highlights](#highlights)
- [Architecture](#architecture)
- [Quick Start](#quick-start)
- [Configuration](#configuration)
- [GraphQL & REST Interfaces](#graphql--rest-interfaces)
- [Quality & Operations](#quality--operations)
- [Troubleshooting](#troubleshooting)
- [Contributing](#contributing)
- [License](#license)

## Overview

SSF is a Spring Boot 3 application exposing a GraphQL API secured with JWT. It integrates with Oracle databases, MinIO object storage, and Spring Actuator health checks to support enterprise-grade deployments.

Key use cases include:

- Authenticating users via username/password and issuing signed JWT access tokens
- Managing user profiles through GraphQL queries and mutations
- Uploading artifacts to MinIO-compatible object storage services
- Monitoring system health with custom contributors for database files, JDBC connections, and MinIO reachability

## Highlights

| Capability | Why it matters |
| --- | --- |
| **JWT-first security** | Strict validation (length, entropy, expiration) with custom filters and GraphQL instrumentation |
| **GraphQL gateway** | Typed schema, mutations for login/logout, and queries for user discovery |
| **Oracle-ready** | Defaults to Oracle JDBC with environment overrides for production |
| **MinIO integration** | Health probes and configuration properties for S3-compatible storage |
| **Observability** | Spring Actuator endpoints and composite health contributors for runtime insights |

## Architecture

```text
clients ─┬─▶ HTTPS (Spring Boot + Jetty @ 8443)
         │    ├─ GraphQL endpoint (/graphql)
         │    ├─ GraphiQL IDE (/graphiql)
         │    └─ REST auth endpoints (/api/auth/**)
         │
         ├─▶ Security Pipeline
         │    ├─ JwtAuthenticationFilter (servlet)
         │    ├─ SecurityFilterChain (access rules)
         │    └─ GraphQLAuthorizationInstrumentation
         │
         ├─▶ Services & Data
         │    ├─ UserService (JPA + Oracle)
         │    ├─ MinIO client (object storage)
         │    └─ AuditService (login/session logging)
         │
         └─▶ Observability
              ├─ Custom health indicators
              └─ Spring Actuator (/actuator/**)
```

## Quick Start

### Prerequisites

- Java 21 (configured via Gradle toolchains)
- Gradle 8+
- Oracle Database reachable at `ORACLE_HOST:ORACLE_PORT`
- (Optional) Docker for MinIO local testing

### 1. Clone & Build

```bash
git clone https://github.com/your-org/graphqlScala.git
cd graphqlScala

# Run unit tests and build artifacts
./gradlew clean build
```

### 2. Prepare Environment

Create a `.env` or export variables in your shell:

```bash
export ORACLE_HOST=localhost
export ORACLE_PORT=1521
export ORACLE_DB=FREEPDB1
export ORACLE_USER=ssfuser
export ORACLE_PASSWORD=ssfuser

export JWT_SECRET="change-me-to-a-32-plus-character-super-secret"

export MINIO_ACCESS_KEY=minioadmin
export MINIO_SECRET_KEY=minioadmin
export MINIO_URL=http://localhost:9000

# Optional: provide a custom SSL keystore password
export KEYSTORE_PASSWORD=changeit
```

> 🔐 **Remember:** `JWT_SECRET` must be at least 32 characters with ≥10 distinct characters (longer secrets improve entropy). The application enforces this at startup.

### 3. Launch the Application

```bash
./gradlew bootRun
```

The server boots with HTTPS on `https://localhost:8443`. Since a development keystore is bundled (`src/main/resources/keystore.p12`), your browser/HTTP client may require a trust override.

### 4. Optional: Start Dependencies with Docker

```bash
# Oracle Database XE (example)
docker run -d --name oracle-xe \
  -p 1521:1521 -p 5500:5500 \
  -e ORACLE_PASSWORD=ssfuser \
  gvenzl/oracle-xe:21-slim

# MinIO
docker run -d --name minio \
  -p 9000:9000 -p 9001:9001 \
  -e MINIO_ROOT_USER=minioadmin \
  -e MINIO_ROOT_PASSWORD=minioadmin \
  quay.io/minio/minio server /data --console-address :9001
```

## Configuration

Spring Boot properties can be set via `application.yml`, profile-specific files, or environment variables. Key properties include:

| Property | Description | Default |
| --- | --- | --- |
| `server.port` | HTTPS port | `8443` |
| `server.ssl.*` | Keystore path, password, alias | Bundled PKCS12 keystore |
| `spring.datasource.url` | Oracle JDBC URL | `jdbc:oracle:thin:@//${ORACLE_HOST}:${ORACLE_PORT}/${ORACLE_DB}` |
| `spring.datasource.username` / `password` | Database credentials | `ssfuser` / `ssfuser` |
| `jwt.secret` | Symmetric signing key | Fallback demo value (override in all environments) |
| `jwt.expiration` | Token lifetime (ms) | `86400000` (1 day) |
| `minio.url` | MinIO endpoint | `http://localhost:9000` |
| `minio.access-key` / `secret-key` | MinIO credentials | `minioadmin` / `minioadmin` |

Profile-specific overrides live under `src/main/resources/application-*.yml`.

## GraphQL & REST Interfaces

### Endpoints

- `POST https://localhost:8443/graphql` — GraphQL operations
- `GET https://localhost:8443/graphiql` — in-browser IDE
- `POST https://localhost:8443/api/auth/login` — REST login
- `POST https://localhost:8443/api/auth/validate` — REST token validation
- `GET https://localhost:8443/actuator/health` — health probe

### Example: Authenticate via GraphQL

```graphql
mutation {
  login(username: "demo", password: "changeit") {
    token
  }
}
```

Use the returned token in the `Authorization` header:

```http
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
```

### Example: Fetch Current User

```graphql
query {
  getUserByUsername(username: "demo") {
    id
    username
    email
  }
}
```

## Quality & Operations

### Test & Coverage

```bash
./gradlew test               # Unit & integration tests
./gradlew jacocoTestReport   # HTML coverage (build/jacocoHtml)
```

### Observability

- Composite health contributor registers `databaseFile`, `databaseConnection`, and `minio`
- Custom Actuator indicator `you` surfaces AI readiness (`{"ai":"I am up and running!"}`)
- Enable additional Actuator endpoints by adjusting `management.endpoints.web.exposure.include`

### Building an OCI Image

```bash
./gradlew bootBuildImage --imageName=ssf-graphql:latest
```

## Troubleshooting

| Symptom | Resolution |
| --- | --- |
| **`IllegalStateException: JWT secret must be provided`** | Set `JWT_SECRET` with ≥32 characters before starting the app |
| **`ORA-01017` authentication errors** | Verify `ORACLE_USER`/`ORACLE_PASSWORD`; if running locally ensure Oracle XE container is healthy |
| **GraphiQL reports `Authentication required`** | Supply a valid JWT token in the `Authorization` header. As a last resort for local development only, you may temporarily disable enforcement in `SecurityConfig`; never commit, push, or enable this bypass outside your machine. Prefer safer alternatives such as generating a valid JWT, using a temporary environment-only feature flag, or mocking auth locally, and audit commits plus CI/CD configs before merge/deploy. |
| **MinIO health check is DOWN** | Confirm MinIO container is reachable and credentials match `minio.*` properties |

## Contributing

1. Fork the repository and create a feature branch: `git checkout -b feature/awesome`
2. Keep changes focused and covered by tests (`./gradlew test`)
3. Submit a pull request describing the change and its motivation

## License

Distributed under the MIT License. See [LICENSE](LICENSE) for full text.

---

<div align="center">
  Crafted with ❤️ using Spring Boot, GraphQL, and a relentless focus on security.
</div>
