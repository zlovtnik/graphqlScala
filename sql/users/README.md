# Oracle User Creation & Privilege Grant Scripts

These scripts are **manual prerequisites** for database deployment and must be executed by a DBA with SYS/DBA privileges **before** running any Flyway migrations.

## ⚠️ Important: Manual Execution Required

These scripts **CANNOT** be run by Flyway migrations because:

- They require SYS/DBA privileges
- Flyway runs as the application user (APP_USER) with limited permissions
- Attempting to run these via Flyway will result in `ORA-01031: insufficient privileges`

## Execution Order

### 1. create_user_with_grants.sql (Production)

Creates the application user `APP_USER` with baseline privileges required for the application to function.

**Prerequisites:**

- Oracle database is running and accessible
- You have SYS/DBA access

**How to run:**

```bash
# Connect as SYS/DBA
sqlplus / as sysdba

# Execute the script (update password as needed)
SQL> @sql/users/create_user_with_grants.sql
```

**What it does:**

- Creates user `APP_USER` with secure password. Provide the password at runtime via SQL*Plus substitution variable (`&APP_USER_PASSWORD`) or use an external secret; do NOT commit passwords to version control.
- Allocates 10GB quota on `users` tablespace
- Grants: CONNECT, RESOURCE, CREATE SESSION/TABLE/VIEW/PROCEDURE/SEQUENCE/TRIGGER/TYPE/SYNONYM
- Grants execute permissions for DBMS_CRYPTO, DBMS_LOCK, DBMS_OUTPUT, UTL_RAW

### 2. create_user_with_debug_grants.sql (Development/Local Only)

Adds debug-only privileges for local development environments. **Never apply in staging, QA, or production without explicit DBA approval.**

**How to run:**

```bash
# Connect as SYS/DBA
sqlplus / as sysdba

# Execute after create_user_with_grants.sql
SQL> @sql/users/create_user_with_debug_grants.sql
```

**What it does:**

- Grants DEBUG CONNECT SESSION to APP_USER
- Grants DEBUG ANY PROCEDURE to APP_USER

## Full Deployment Workflow

1. **Manual Pre-Migration Setup (DBA only):**

   ```bash
   # Run these as SYS/DBA before Flyway migrations
   @sql/users/create_user_with_grants.sql
   @sql/users/create_user_with_debug_grants.sql  # Dev only
   ```

2. **Automated Migrations (Application deployment):**

   ```bash
   ./gradlew bootRun  # Triggers Flyway migrations
   # OR
   ./gradlew bootBuildImage  # Builds container with migrations
   ```

3. **Docker/Container Deployment:**

   - The `docker-entrypoint-initdb.d/01-init-user.sh` script handles user creation automatically in Docker
   - For manual deployments, run the scripts above as DBA first

## Password Management

**Development:**

- Use SQL*Plus/SQLcl substitution variable `&APP_USER_PASSWORD` to supply the password, or provide via an external secret manager. DO NOT commit passwords to source control.
- Update this in the script or pass via environment variable before executing

**Production:**

- Generate a strong password (≥32 characters, mixed case, numbers, special characters)
- Never commit passwords to version control
- Use `.secrets/oracle-password` file (chmod 600) or environment variables
- Set via `DB_USER_PASSWORD` environment variable

## Troubleshooting

### ORA-01031: insufficient privileges

- **Cause:** You're trying to run the script as APP_USER instead of SYS/DBA
- **Solution:** Connect as `sqlplus / as sysdba` first

### ORA-00922: invalid option

- **Cause:** SQL*Plus substitution syntax (`&PASSWORD`) attempted in Flyway
- **Solution:** These scripts are not meant for Flyway; use manual execution only

### User already exists

- **Cause:** Running the script multiple times
- **Solution:** Either drop the user first (`DROP USER APP_USER CASCADE;`) or modify the script to check existence first

## Related Scripts

- **db/migration/** – Flyway-managed migrations (run by application on startup)
- **docker-entrypoint-initdb.d/01-init-user.sh** – Docker initialization script
- **scripts/partition-maintenance.sh** – Post-deployment partition management

## Documentation References

- Oracle User Security: See `docs/SECURITY_ARCHITECTURE.md`
- Deployment Guide: See `README.md` (Deployment section)
- Help & Troubleshooting: See `HELP.md`
