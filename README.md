<div align="center">
  <h1>⚡ ORACLE – Enterprise GraphQL Authority</h1>
  <p><strong>Production-Grade GraphQL Gateway</strong> | Fortress Security | Cloud-Native | Enterprise-Hardened</p>
  <p><em>\"When speed meets security. When scale meets simplicity. When enterprise demands elegance.\"</em></p>
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

> **ORACLE** is a premium GraphQL platform engineered for enterprises that demand uncompromising security, lightning-fast performance, and transparent observability. Built with Spring Boot 3, Oracle Database, and cloud-native resilience patterns—ORACLE transforms complex backend architectures into elegant, typesafe GraphQL APIs.

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

ORACLE is the GraphQL intelligence layer for enterprises. A Spring Boot 3 powerhouse that seamlessly orchestrates complex backend architectures—Oracle databases, distributed storage, caching layers—and exposes them through a single, typesafe GraphQL interface.

**Not your typical API gateway.** ORACLE brings:

- **Zero-Trust Security**: JWT validation meets field-level authorization, cryptographically enforced per request
- **Audit-First Design**: Every login, every query, every permission check is immutably logged
- **Enterprise Compliance**: GDPR, SOX, and HIPAA-ready roadmap with multi-phase security hardening
- **Performance at Scale**: Caffeine + Redis caching with circuit breakers for distributed resilience

Key use cases include:

- Authenticating users via username/password and issuing signed JWT access tokens
- Managing user profiles through GraphQL queries and mutations
- Uploading artifacts to MinIO-compatible object storage services
- Monitoring system health with custom contributors for database files, JDBC connections, and MinIO reachability

## Why ORACLE Wins

✅ **Built for scale** – Oracle partitioning, Redis clustering, connection pooling optimized for millions of QPS  
✅ **Security DNA** – Zero-trust from first request; JWT entropy validation + field-level auth enforced at GraphQL layer  
✅ **Compliance-native** – GDPR/SOX roadmap; immutable audit logs; secrets rotation; encryption strategies  
✅ **DevOps-friendly** – Spring Boot + Docker + Kubernetes native; self-contained Jetty with TLS built-in  
✅ **Observable end-to-end** – Micrometer metrics, distributed traces, custom health contributors, Grafana-ready  
✅ **Open & extensible** – Spring ecosystem, GraphQL-Java, Flyway migrations—plug in your tools, keep your freedom  

## Highlights

| **Capability** | **Enterprise Edge** |
| --- | --- |
| **Zero-Trust Execution** | Cryptographic JWT validation + field-level GraphQL authorization—every operation audited |
| **Type-Safe GraphQL API** | Lean schema, mutation-driven workflows (login/MFA/profile), query-driven discovery |
| **Multi-Tenant Ready** | Oracle partitioning + Redis isolation = scale to millions without breaking a sweat |
| **S3 & Object Storage** | Native MinIO + S3-compatible integrations with health probes and retry logic |
| **Real-Time Observability** | Distributed tracing, composite health indicators, Prometheus-ready metrics |
| **Compliance Roadmap** | Phase-driven MFA (TOTP, WebAuthn, SMS), immutable audit logs, TDE encryption, dynamic RBAC |
| **99.9% SLA Architecture** | Circuit breakers, connection pooling, bulkhead isolation—resilience by design |

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

### System Requirements

- **Java 21** (LTS, configured via Gradle toolchains)  
- **Gradle 8+** (modern build orchestration)  
- **Oracle Database 19c+** (or Oracle XE for dev)  
- **Redis 7.4+** (cache/session store)  
- **(Optional)** Docker + MinIO for local S3-compatible testing

### Database Setup

> ⚠️ **IMPORTANT:** The following manual setup steps must be performed by a DBA with SYS/DBA privileges **before** running the application or Flyway migrations.

1. **Create the application user** (run as SYS or DBA):

   Execute the scripts in `sql/users/` as SYS/DBA:

   ```bash
   sqlplus / as sysdba
   SQL> @sql/users/create_user_with_grants.sql
   # For development only:
   SQL> @sql/users/create_user_with_debug_grants.sql
   ```

   See [`sql/users/README.md`](sql/users/README.md) for full deployment instructions and password management.

2. **Automated Schema Setup** (run via application):

   The application uses Flyway to automatically create tables, sequences, and indexes on first startup. This is managed by:

   ```bash
   ./gradlew bootRun
   # OR
   docker-compose up  # Includes automatic user creation via docker-entrypoint-initdb.d/01-init-user.sh
   ```

3. **Default Admin User**:

   When the application starts for the first time, a default admin user is created with:
   - **Username:** `admin`
   - **Password:** `Admin@123` (hashed in the database)

   > ⚠️ **Security Warning:** Change the default admin password immediately after first login.

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
export ORACLE_USER=APP_USER
export ORACLE_PASSWORD=APP_USER

export JWT_SECRET="paste-a-random-32-plus-character-secret-here"

export MINIO_ACCESS_KEY=minioadmin
export MINIO_SECRET_KEY=minioadmin
export MINIO_URL=http://localhost:9000

# Optional: provide a custom SSL keystore password
export KEYSTORE_PASSWORD=changeit
```

> 🔐 **Security Hardened:** `JWT_SECRET` enforces **entropy validation**—32+ chars with `min(20, length/2)` distinct characters. For a 32-char secret, you need 16+ unique chars. This isn't paranoia; this is how enterprises stay breached-free.

> ⚠️ **Database Password Warning:** For production deployments, DO NOT use the default database password `APP_USER` or any development-only value like `DevOnly_Password123!`. Set `DB_USER_PASSWORD` (or `ORACLE_PASSWORD`) to a strong, unique value in your deployment environment. See `docs/SECURITY_ARCHITECTURE.md` for guidance on secrets management.

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

#### SSL/TLS & Postman Setup for Local Development

The application uses a self-signed certificate in the bundled keystore for local development. To test the GraphQL API with Postman:

1. **Import the Self-Signed Certificate** (Recommended for development):
   - Open Postman → **Settings** (⚙️ icon) → **Certificates** → **CA Certificates**
   - Click **Select File** and choose `src/main/resources/keystore.p12`
   - Enter password: `changeit` (default development keystore password)
   - Reload Postman

2. **Alternative: Temporarily Disable SSL Verification** (NOT recommended for production):
   - Open **Settings** (⚙️ icon) → **General** → **SSL certificate verification**: toggle **OFF**
   - ⚠️ **Warning:** Only for development. Re-enable for any sensitive work.

3. **Use the Postman Collection**:
   - Import `SSF-GraphQL-Postman-Collection.json` into Postman
   - Update environment variables:
     - `base_url`: Should resolve to `https://localhost:8443` (already set)
     - `username` / `password`: Set to test account credentials
   - All requests in the collection use the standardized full URL structure with explicit host, port, and path

**For Production Deployments:**
- Replace `keystore.p12` with a certificate signed by a trusted Certificate Authority
- Set `strictSSL: true` (enforced by default in the Postman collection)
- Use valid DNS names and update environment variables accordingly

### 4. Optional: Start Dependencies with Docker

```bash
# Oracle Database XE (example - development only, replace password before production)
docker run -d --name oracle-xe \
  -p 1521:1521 -p 5500:5500 \
  -e ORACLE_PASSWORD=DevOnly_Password123! \
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
| `spring.datasource.username` / `password` | Database credentials | No | `APP_USER` / `APP_USER` |
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

**Content Security Policy (CSP):**

The application implements a strict Content Security Policy (CSP) without `'unsafe-inline'` directives to prevent XSS attacks. CSP nonces are automatically generated per request by the `CspHeaderFilter` and enforced at the CDN and backend levels. All inline scripts and styles use external files exclusively, or (if necessary) are protected with cryptographically secure nonces.

For detailed CSP implementation, architecture, and troubleshooting, see [`docs/CSP_IMPLEMENTATION.md`](docs/CSP_IMPLEMENTATION.md).

**Cross-Origin Resource Sharing (CORS):**

The application configures CORS to allow requests from `http://localhost:4200` for development purposes. This enables the Angular frontend running on the default development port to communicate with the backend. In production, CORS should be restricted to trusted origins only. The CORS policy is defined in `SecurityConfig.java` and allows GET, POST, PUT, DELETE, and OPTIONS methods with credentials.

**Breaking Change:** `JWT_SECRET`, `MINIO_ACCESS_KEY`, and `MINIO_SECRET_KEY` no longer have unsafe default values. All three must be explicitly set via environment variables or the application will fail at startup with a clear error message.

### Local development secrets

For non-production work, source secrets from an ignored file instead of hardcoding them in `application.yml`. One simple approach is to create a `.env.local` (listed in `.gitignore`) containing only development credentials, then run `set -a && source .env.local && set +a` before `./gradlew bootRun`. This keeps local experimentation convenient without ever committing secrets. Production deployments should continue to rely on a secrets manager or orchestration platform to inject `JWT_SECRET` and other sensitive values at runtime.

Profile-specific overrides live under `src/main/resources/application-*.yml`.

### Oracle Credential Security

The partition maintenance script (`scripts/partition-maintenance.sh`) and database connections use secure credential handling to prevent password exposure. For detailed setup instructions including:

- Setting up a secure password file (chmod 600)
- Configuring the Oracle External Password Store (Wallet)
- Kubernetes/Docker deployment with secrets
- Auditing and monitoring best practices

See [`docs/SECURITY_ARCHITECTURE.md`](docs/SECURITY_ARCHITECTURE.md).

**Quick Start:**
```bash
# Create secrets directory
mkdir -p .secrets && chmod 700 .secrets

# Store database password securely
printf '%s' "your-secure-password" > .secrets/oracle-password
chmod 600 .secrets/oracle-password

# Add to .gitignore (already included)
echo ".secrets/" >> .gitignore

# Test partition maintenance
./scripts/partition-maintenance.sh
```

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

### Postman Collections & SSL Verification

- `SSF-GraphQL-Postman-Collection.json` is the hardened collection used for shared staging/production testing. It now enforces `strictSSL=true`, so Postman must trust the certificate chain before requests execute. Import the Jetty dev certificate into your OS/Postman trust store or configure Postman *Settings → Certificates* to trust `https://localhost:8443`.
- `postman-collection.json` is the lightweight developer-focused collection that drives the GraphQL samples in this repo. Its requests rely on the `base_url` variable (default `https://localhost:8443`), so you only need to change that single variable to target another stack. URLs are built with a single `{{base_url}}/graphql` string to avoid accidentally duplicating the protocol prefix.
- **Local override:** If you cannot trust the dev certificate, duplicate the production collection inside Postman and set `protocolProfileBehavior.strictSSL=false` *only* in that private copy. Never disable strict SSL in workspace-wide or shared collections.

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

## Security & Compliance

SSF implements a comprehensive security roadmap targeting GDPR and SOX compliance. See documentation for details:

- **[SECURITY_ARCHITECTURE.md](docs/SECURITY_ARCHITECTURE.md)**: Current authentication flow, baseline security controls, and risk assessment
- **[COMPLIANCE_ACCEPTANCE_CRITERIA.md](docs/COMPLIANCE_ACCEPTANCE_CRITERIA.md)**: GDPR/SOX requirements mapped to implementation phases
- **[MFA_IMPLEMENTATION.md](docs/MFA_IMPLEMENTATION.md)**: Phase 1 design for multi-factor authentication (TOTP, SMS, WebAuthn, backup codes)
- **[PHASE_0_DELIVERY_SUMMARY.md](docs/PHASE_0_DELIVERY_SUMMARY.md)**: Delivery timeline, risk assessment, and resource requirements

### Security Roadmap Phases

| Phase | Focus | ETA | Key Deliverables |
|-------|-------|-----|------------------|
| **Phase 0 – Foundations & Readiness** ✅ | Architecture inventory, baseline controls | ✅ Complete | Security docs, compliance matrix, Grafana placeholders |
| **Phase 1 – MFA Stack** 🟡 | TOTP, SMS, WebAuthn, recovery codes | Q1 2026 | MFA module, database migrations, GraphQL APIs |
| **Phase 2 – Audit & Compliance** | Immutable audit logs, data subject rights | Q1-Q2 2026 | Normalized audit schema, export/retention policies |
| **Phase 3 – Data Encryption** | TDE, app-level crypto, key rotation | Q2-Q3 2026 | EncryptionService, HSM integration, key management |
| **Phase 4 – Advanced RBAC** | Field-level authorization, policy engine | Q3-Q4 2026 | Role hierarchy, dynamic policies, permission audit |

### Baseline Security Controls

- ✅ JWT-based authentication (HS256 with entropy validation)
- ✅ Stateless API (no server-side sessions)
- ✅ Route-level authorization enforced in SecurityFilterChain
- ✅ GraphQL operation-level authentication (before data fetchers)
- ✅ Content Security Policy (CSP) headers with nonce generation
- 🟡 Multi-factor authentication (Phase 1 in progress)
- 🔴 Field-level access control (Phase 4)
- 🔴 Transparent Data Encryption (Phase 3)

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
