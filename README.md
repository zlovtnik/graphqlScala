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
- Redis 7+ reachable at `REDIS_HOST:REDIS_PORT`
- (Optional) Docker for MinIO/Redis local testing

### Database Setup

1. **Create the application user** (run as SYS or DBA):

   ```sql
   -- Run one of these scripts to create the user and basic grants
   @create_user_with_grants.sql
   -- OR for development with debug privileges
   @create_user_with_debug_grants.sql
   ```

2. **Set up the schema** (run as the application user, e.g., `ssfuser`):

   ```sql
   @master.sql
   ```

3. **Apply additional grants** (run as SYS or DBA):

   ```sql
   @grant_privileges.sql
   ```

### Default Admin User

When the application starts for the first time, a default admin user is created with:
- **Username:** `admin`
- **Password:** `Admin@123` (hashed in the database)

> ⚠️ **Security Warning:** Change the default admin password immediately after first login. The default password is for initial setup only and should never be used in production.

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

export JWT_SECRET="paste-a-random-32-plus-character-secret-here"

export MINIO_ACCESS_KEY=minioadmin
export MINIO_SECRET_KEY=minioadmin
export MINIO_URL=http://localhost:9000

# Optional: provide a custom SSL keystore password
export KEYSTORE_PASSWORD=changeit
```

> 🔐 **Remember:** `JWT_SECRET` must be at least 32 characters long and include at least `min(20, length/2)` distinct characters. For example, a 32-character secret must contain 16 distinct characters. The application enforces this requirement at startup.

### Required Environment Variables

The following environment variables **MUST** be set before starting the application. The application will fail fast with a clear error message if any are missing:

| Environment Variable | Purpose | Notes |
| --- | --- | --- |
| `JWT_SECRET` | Symmetric key for signing and validating JWT tokens | Must be ≥32 characters and contain at least `min(20, length/2)` distinct characters (e.g., 16 distinct characters for a 32-char secret). |
| `MINIO_ACCESS_KEY` | Access key for MinIO object storage authentication | Cannot use default values; must be explicitly set. |
| `MINIO_SECRET_KEY` | Secret key for MinIO object storage authentication | Cannot use default values; must be explicitly set. |

**Example: Setting Strong Credentials**

```bash
# Generate a secure 32+ character JWT_SECRET
export JWT_SECRET=$(openssl rand -base64 32)

# MinIO credentials (use strong, unique values in production)
export MINIO_ACCESS_KEY=$(openssl rand -base64 16)
export MINIO_SECRET_KEY=$(openssl rand -base64 32)
```

### Secrets Management for Production

**IMPORTANT:** Never commit or hardcode secrets in your application. For production deployments, use a dedicated secrets manager:

#### HashiCorp Vault

```bash
# 1. Install Vault CLI and authenticate
vault login -method=ldap username=<your-username>

# 2. Retrieve secrets and set environment variables
export JWT_SECRET=$(vault kv get -field=jwt_secret secret/ssf/prod)
export MINIO_ACCESS_KEY=$(vault kv get -field=access_key secret/ssf/prod)
export MINIO_SECRET_KEY=$(vault kv get -field=secret_key secret/ssf/prod)

# 3. Start the application
./gradlew bootRun
```

#### AWS Secrets Manager

```bash
# 1. Install AWS CLI and configure credentials
aws configure

# 2. Retrieve secrets and set environment variables
export JWT_SECRET=$(aws secretsmanager get-secret-value --secret-id ssf/jwt_secret --query SecretString --output text)
export MINIO_ACCESS_KEY=$(aws secretsmanager get-secret-value --secret-id ssf/minio_access_key --query SecretString --output text)
export MINIO_SECRET_KEY=$(aws secretsmanager get-secret-value --secret-id ssf/minio_secret_key --query SecretString --output text)

# 3. Start the application
./gradlew bootRun
```

#### Docker / Kubernetes

For containerized environments, inject secrets via:

- **Docker:** Use `docker run --env-file .env` or Docker Secrets
- **Kubernetes:** Use Kubernetes Secrets mounted as environment variables or files
- **Docker Compose:** Reference secrets in `.env` file (keep `.env` outside version control)

Example `docker-compose.yml` with secrets:

```yaml
version: '3.8'
services:
  app:
    image: ssf-graphql:latest
    environment:
      JWT_SECRET: ${JWT_SECRET}
      MINIO_ACCESS_KEY: ${MINIO_ACCESS_KEY}
      MINIO_SECRET_KEY: ${MINIO_SECRET_KEY}
    # ... other configuration
```

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

# Redis Cache
docker run -d --name redis-cache \
  -p 6379:6379 \
  redis:7.4-alpine
```

## Configuration

Spring Boot properties can be set via `application.yml`, profile-specific files, or environment variables. Key properties include:

| Property | Description | Required | Default |
| --- | --- | --- | --- |
| `server.port` | HTTPS port | No | `8443` |
| `server.ssl.*` | Keystore path, password, alias | No | Bundled PKCS12 keystore |
| `spring.datasource.url` | Oracle JDBC URL | No | `jdbc:oracle:thin:@//${ORACLE_HOST}:${ORACLE_PORT}/${ORACLE_DB}` |
| `spring.datasource.username` / `password` | Database credentials | No | `ssfuser` / `ssfuser` |
| `spring.redis.host` | Redis server hostname | No | `localhost` |
| `spring.redis.port` | Redis server port | No | `6379` |
| `app.jwt.secret` | Symmetric signing key for JWT | **YES** | **None** (must be set via `JWT_SECRET` environment variable) |
| `jwt.expiration` | Token lifetime (ms) | No | `86400000` (1 day) |
| `app.minio.url` | MinIO endpoint | No | `http://localhost:9000` |
| `app.minio.access-key` | MinIO credentials | **YES** | **None** (must be set via `MINIO_ACCESS_KEY` environment variable) |
| `app.minio.secret-key` | MinIO credentials | **YES** | **None** (must be set via `MINIO_SECRET_KEY` environment variable) |
| `security.password.bcrypt.strength` | BCrypt cost factor for password hashing (4-31) | No | `12` |

**BCrypt Strength Configuration:**

The `security.password.bcrypt.strength` property controls the computational cost of password hashing. Valid range is 4-31, with higher values providing better security but slower performance:

- **Strength 10**: ~100ms per hash (suitable for development)
- **Strength 12**: ~400ms per hash (balanced security/performance)
- **Strength 14**: ~1600ms per hash (high security)

When increasing strength in production, load-test authentication endpoints to ensure acceptable response times. The default of 12 provides strong security for most deployments.

**Breaking Change:** `JWT_SECRET`, `MINIO_ACCESS_KEY`, and `MINIO_SECRET_KEY` no longer have unsafe default values. All three must be explicitly set via environment variables or the application will fail at startup with a clear error message.

### Local development secrets

For non-production work, source secrets from an ignored file instead of hardcoding them in `application.yml`. One simple approach is to create a `.env.local` (listed in `.gitignore`) containing only development credentials, then run `set -a && source .env.local && set +a` before `./gradlew bootRun`. This keeps local experimentation convenient without ever committing secrets. Production deployments should continue to rely on a secrets manager or orchestration platform to inject `JWT_SECRET` and other sensitive values at runtime.

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

### Performance Testing

The application includes Gatling performance tests that can be configured for different environments. Since Gatling is included as a test dependency, simulations run as standard Java applications:

```bash
# Run performance tests against local environment (default)
./gradlew test --tests "*UserSimulation*"

# Run against different base URL via system property
./gradlew test --tests "*UserSimulation*" -Dbase.url=https://staging.example.com

# Run against different base URL via environment variable
BASE_URL=https://production.example.com ./gradlew test --tests "*UserSimulation*"

# For CI environments with self-signed certificates, configure JVM truststore
./gradlew test --tests "*UserSimulation*" -Djavax.net.ssl.trustStore=/path/to/truststore.jks -Djavax.net.ssl.trustStorePassword=password
```

**Performance Test Configuration:**

- **Base URL**: Configurable via `base.url` system property or `BASE_URL` environment variable
- **Default**: `https://localhost:8443` (for local development)
- **SSL**: Uses JVM's default truststore; override with system properties for custom certificates
- **Load Profile**: 5,000 users ramping over 3 minutes, then 50 users/sec for 2 minutes
- **Assertions**: 95% of requests under 1 second, max 5 seconds, 95% success rate

### Observability

- Composite health contributor registers `databaseFile`, `databaseConnection`, and `minio`
- Custom Actuator indicator `you` surfaces AI readiness (`{"ai":"I am up and running!"}`)
- Enable additional Actuator endpoints by adjusting `management.endpoints.web.exposure.include`

### Partition Maintenance Job

The rolling partition script (`scripts/partition-maintenance.sh`) now refuses to embed credentials on the command line. Instead, it reads the Oracle password from a local file with `600` permissions. By default the script looks for `.secrets/oracle-password` at the repo root, or you can point to another location via `ORACLE_PASSWORD_FILE`.

```bash
mkdir -p .secrets
printf 'super-secret-password' > .secrets/oracle-password
chmod 600 .secrets/oracle-password

# optional: override location
export ORACLE_PASSWORD_FILE=$PWD/.secrets/oracle-password

./scripts/partition-maintenance.sh
```

Because SQL*Plus now receives the password via stdin, it no longer appears in process listings or shell history. Metrics continue to land in `metrics/partition-maintenance.prom` by default and can be overridden with `PARTITION_METRICS_FILE`.

### Building an OCI Image

```bash
./gradlew bootBuildImage --imageName=ssf-graphql:latest
```

## Troubleshooting

| Symptom | Resolution |
| --- | --- |
| **`IllegalStateException: Missing required environment variables`** | Set `JWT_SECRET`, `MINIO_ACCESS_KEY`, and `MINIO_SECRET_KEY` environment variables before starting the app. See [Required Environment Variables](#required-environment-variables) section above. |
| **`IllegalStateException: JWT secret must be provided`** | Set `JWT_SECRET` with ≥32 characters before starting the app |
| **`ORA-01017` authentication errors** | Verify `ORACLE_USER`/`ORACLE_PASSWORD`; if running locally ensure Oracle XE container is healthy |
| **`RedisConnectionFailureException: Unable to connect to Redis`** | Start Redis locally (`docker run redis:7.4-alpine` or `brew services start redis`) or set `REDIS_HOST/PORT` so the app can reach an existing instance. |
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
