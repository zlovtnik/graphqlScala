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

Copy/paste environment template:

```bash
export ORACLE_HOST=localhost
export ORACLE_PORT=1521
export ORACLE_DB=FREEPDB1
export ORACLE_USER=ssfuser
export ORACLE_PASSWORD=ssfuser

export MINIO_URL=http://localhost:9000
export MINIO_ACCESS_KEY=minioadmin
export MINIO_SECRET_KEY=minioadmin

export JWT_SECRET="replace-with-long-unique-string"
export KEYSTORE_PASSWORD=changeit
```

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
| `IllegalStateException: JWT secret must be provided` | Missing or weak `JWT_SECRET` | Export a ≥32 char secret before starting the app |
| HTTPS connection refused | Keystore not trusted | Import `src/main/resources/keystore.p12` into local trust store or use REST client with `--insecure` for dev |
| Oracle connection fails (`ORA-01017`) | Bad credentials or DB offline | Verify env vars, confirm container/instance is healthy |
| MinIO health check DOWN | MinIO not running or invalid credentials | Start container and align `minio.*` properties with environment |
| GraphQL `AccessDeniedException` | Missing token header | Provide `Authorization: Bearer <token>` in GraphiQL/HTTP client |

## 🔍 Useful Logs & Endpoints

- Application logs: `build/logs/` (if configured) or console output.
- Actuator health: `GET https://localhost:8443/actuator/health`
- JWT validation audit trail: `AuditService` logs in `com.example.ssf.service`.

Enable verbose security logging during investigation:

```properties
# src/main/resources/application.properties (temporary for debugging)
logging.level.org.springframework.security=DEBUG
```

## 🤝 Need More Help?

- Consult Spring Boot reference docs for specific starters.
- Check `README.md` for architecture diagrams, deployment advice, and extended troubleshooting.
- Reach out to the team with logs, request payload, and environment details.

Stay secure, keep tokens secret, and happy querying! ✨
