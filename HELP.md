# SSF GraphQL Platform Help Center

This guide distills the essential commands, workflows, and troubleshooting tactics for day-to-day development with the SSF GraphQL Platform.

## 🔗 Quick Links

- Project overview & architecture: see [README.md](./README.md)
- GraphQL schema: `src/main/resources/graphql/schema.graphqls`
- OAuth & security components: `src/main/java/com/example/ssf/security`
- Health monitoring: `src/main/java/com/example/ssf/HealthConfig.java`

## 🚀 Daily Developer Workflow

| Task | Command |
| --- | --- |
| Clean & build | `./gradlew clean build` |
| Run app (HTTPS @ 8443) | `./gradlew bootRun` |
| Launch with profile | `SPRING_PROFILES_ACTIVE=dev ./gradlew bootRun` |
| Run tests | `./gradlew test` |
| Generate coverage report | `./gradlew jacocoTestReport` |
| Build OCI image | `./gradlew bootBuildImage --imageName=ssf-graphql:latest` |

> **Tip:** Gradle toolchains install the required JDK 21 automatically—no manual JVM switching needed.

## ⚙️ Environment Checklist

1. **Database** – Oracle reachable at `ORACLE_HOST:ORACLE_PORT` (default `localhost:1521`).
2. **MinIO** – optional but recommended for object storage (`http://localhost:9000`).
3. **Secrets** – export a high-entropy `JWT_SECRET` (≥32 chars, ≥10 unique chars).
4. **SSL** – bundled keystore (`src/main/resources/keystore.p12`) usable for local HTTPS.

5. **Repository Type (optional)** – The application can be configured to use either blocking JDBC (DataSource) or reactive R2DBC connections:

  - To use JDBC (blocking): activate the `jdbc` profile: `-Dspring.profiles.active=jdbc` or `SPRING_PROFILES_ACTIVE=jdbc` and configure `spring.datasource.*` properties (URL, username, password). This profile also enables the blocking `DataSource` bean and disables the default reactive DataSource auto-configuration.

  - To use R2DBC (reactive): set `app.datasource.enabled=false` (default) or omit it, and provide `spring.r2dbc.url`, `spring.r2dbc.username`, and `spring.r2dbc.password`. Ensure the R2DBC driver is on the classpath (e.g., `spring-boot-starter-data-r2dbc` and a db-specific driver). The default profile excludes the JDBC `DataSource` autos-configuration to prefer reactive mode; use `jdbc` profile to enable blocking mode.

Copy/paste environment template:

```bash
export ORACLE_HOST=localhost
export ORACLE_PORT=1521
export ORACLE_DB=XEPDB1
export ORACLE_USER=APP_USER
export ORACLE_PASSWORD=APP_USER

export MINIO_URL=http://localhost:9000
export MINIO_ACCESS_KEY=minioadmin
export MINIO_SECRET_KEY=minioadmin

export JWT_SECRET="replace-with-long-unique-string"
export KEYSTORE_PASSWORD=changeit
```

> **Note:** `ORACLE_DB=XEPDB1` matches the application default (app.r2dbc.database property). Docker local environments use FREEPDB1; update `ORACLE_DB` accordingly when connecting to those deployments.
> **WARNING:** The `ORACLE_USER` and `ORACLE_PASSWORD` values above are development defaults only. Never use these in production; set them to strong, unique credentials.

## 🧪 Exercising the Platform

### REST Authentication Flow

1. `POST /api/auth/login` with JSON body `{"username":"demo","password":"changeit"}`.
2. Receive `AuthResponse.token` and store it client-side.
3. Include header `Authorization: Bearer <token>` for subsequent requests.

### GraphQL via GraphiQL

- URL: `https://localhost:8443/graphiql`
- Set HTTP header: `Authorization: Bearer <token>`
- Sample query:

```graphql
query GetUser {
  getUserByUsername(username: "demo") {
    id
    username
    email
  }
}
```

### Object Storage Smoke Test

```bash
mc alias set local http://localhost:9000 minioadmin minioadmin
mc ls local
```

## 🛠 Troubleshooting Playbook

| Problem | Root Cause | Fix |
| --- | --- | --- |
| `UsernameNotFoundException: User not found: <username>` | User doesn't exist in database | Create user first: `TEST_PASSWORD=pass ./create_test_user.sh username email` |
| `GraphQL error: "User not found... Please create an account first"` | Login attempt with non-existent user | Use `createUser` mutation or REST endpoint to register first |
| `GraphQL error: "Authentication failed" (INVALID_CREDENTIALS)` | User exists but password is wrong | Verify password is correct or reset via password recovery |
| `IllegalStateException: JWT secret must be provided` | Missing or weak `JWT_SECRET` | Export a ≥32 char secret before starting the app |
| HTTPS connection refused | Keystore not trusted | Import `src/main/resources/keystore.p12` into local trust store or use REST client with `--insecure` for dev |
| Oracle connection fails (`ORA-01017`) | Bad credentials or DB offline | Verify env vars, confirm container/instance is healthy |
| MinIO health check DOWN | MinIO not running or invalid credentials | Start container and align `minio.*` properties with environment |
| GraphQL `AccessDeniedException` | Missing token header | Provide `Authorization: Bearer <token>` in GraphiQL/HTTP client |

### Authentication Error Handling (v1.1+)

The platform now provides clear, actionable error messages for authentication failures:

- **User not found**: Returns specific error code `USER_NOT_FOUND` with guidance to create an account
- **Bad credentials**: Returns `INVALID_CREDENTIALS` error code when user exists but password is wrong
- **Token validation**: Invalid or expired tokens are silently ignored (graceful degradation)

For debugging, enable verbose security logging:

```properties
# src/main/resources/application.properties (temporary for debugging)
logging.level.org.springframework.security=DEBUG
logging.level.com.rcs.ssf.security=DEBUG
logging.level.com.rcs.ssf.graphql=DEBUG
```

## 🔍 Useful Logs & Endpoints

- Application logs: `build/logs/` (if configured) or console output.
- Actuator health: `GET https://localhost:8443/actuator/health`
- JWT validation audit trail: `AuditService` logs in `com.rcs.ssf.service`.

Enable verbose security logging during investigation:

## 🤝 Need More Help?

- Consult Spring Boot reference docs for specific starters.
- Check `README.md` for architecture diagrams, deployment advice, and extended troubleshooting.
- Reach out to the team with logs, request payload, and environment details.

Stay secure, keep tokens secret, and happy querying! ✨
