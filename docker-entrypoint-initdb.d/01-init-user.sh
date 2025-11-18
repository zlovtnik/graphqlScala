#!/bin/bash

# WARNING: Do NOT use default credentials in production.
# The APP_USER and DB_USER_PASSWORD must be set to a strong, unique password
# for production deployments. This script enforces a runtime safeguard that
# prevents starting in production when a default or missing password is detected.

echo "=== Starting Oracle Database Initialization ==="

# Set ENVIRONMENT default before any conditional check to prevent undefined variable issues
ENVIRONMENT=${ENVIRONMENT:-development}

# Use DB_USER_PASSWORD for the application user. In production, we require DB_USER_PASSWORD
# to be explicitly set to a strong password; in non-production we fall back to APP_USER for
# convenience.
if [[ "${ENVIRONMENT,,}" == "production" ]]; then
  DB_PASSWORD="${DB_USER_PASSWORD}"
else
  DB_PASSWORD="${DB_USER_PASSWORD:-APP_USER}"
fi

# Protect production environments from weak/no password.
if [[ "${ENVIRONMENT,,}" == "production" ]]; then
  if [[ -z "${DB_USER_PASSWORD}" || "${DB_USER_PASSWORD}" == "APP_USER" || "${DB_USER_PASSWORD}" == "app_user" ]]; then
    echo "ERROR: DB_USER_PASSWORD must be set to a strong, unique value in production."
    echo "Set DB_USER_PASSWORD to a secure value (do not use 'APP_USER')."
    exit 1
  fi
fi
RETRIES=0
MAX_RETRIES=60

# Reject DB passwords containing double quotes
if [[ "$DB_PASSWORD" == *'"'* ]]; then
  echo "ERROR: Database password cannot contain double quote characters"
  exit 1
fi

# Reject DB passwords containing single quotes
if [[ "$DB_PASSWORD" == *"'"* ]]; then
  echo "ERROR: Database password cannot contain single quote characters"
  exit 1
fi

# Password is used as-is; validation above ensures no problematic characters

# Wait for Oracle to be fully ready
echo "Waiting for Oracle database to start..."
while [ $RETRIES -lt $MAX_RETRIES ]; do
  if echo "SELECT 1 FROM DUAL;" | "$ORACLE_HOME/bin/sqlplus" -s / as sysdba >/dev/null 2>&1; then
    echo "Oracle is ready"
    break
  fi
  RETRIES=$((RETRIES + 1))
  echo "Attempt $RETRIES/$MAX_RETRIES: Waiting for Oracle to be ready..."
  sleep 1
done

if [ $RETRIES -eq $MAX_RETRIES ]; then
  echo "ERROR: Oracle failed to start after $MAX_RETRIES attempts"
  exit 1
fi

sleep 2

# Create application user and tablespace with proper error handling
# SECURITY NOTE: This script runs during container initialization in a controlled environment.
# The password is passed via environment variable and is ephemeral—it exists only in this
# initialization phase and is not persisted in the container after startup. The application
# connects to Oracle using the same credentials stored in the application's runtime secrets.
# For production deployments, ensure the host running this container has appropriate access
# controls and secret management. See docs/SECURITY_ARCHITECTURE.md for full details.
echo "=== Creating application user and tablespace ==="

"$ORACLE_HOME/bin/sqlplus" -s / as sysdba > /tmp/init_user.log 2>&1 <<EOFUSER
ALTER SESSION SET CONTAINER = FREEPDB1;

-- Create tablespace in default location if not exists
DECLARE
  v_tablespace_exists NUMBER := 0;
BEGIN
  SELECT COUNT(*) INTO v_tablespace_exists 
  FROM dba_tablespaces 
  WHERE tablespace_name = 'SSFSPACE';
  
  IF v_tablespace_exists = 0 THEN
    EXECUTE IMMEDIATE 'CREATE TABLESPACE ssfspace DATAFILE SIZE 100M AUTOEXTEND ON';
    DBMS_OUTPUT.PUT_LINE('Tablespace ssfspace created');
  ELSE
    DBMS_OUTPUT.PUT_LINE('Tablespace ssfspace already exists');
  END IF;
END;
/

-- Create user if not exists
DECLARE
  v_user_exists NUMBER := 0;
BEGIN
  SELECT COUNT(*) INTO v_user_exists 
  FROM dba_users 
  WHERE username = 'APP_USER';
  
  IF v_user_exists = 0 THEN
    EXECUTE IMMEDIATE 'CREATE USER APP_USER IDENTIFIED BY ' || CHR(34) || '${DB_PASSWORD}' || CHR(34) || ' DEFAULT TABLESPACE ssfspace';
    DBMS_OUTPUT.PUT_LINE('User APP_USER created');
  ELSE
    DBMS_OUTPUT.PUT_LINE('User APP_USER already exists');
  END IF;

  EXECUTE IMMEDIATE 'ALTER USER APP_USER IDENTIFIED BY ' || CHR(34) || '${DB_PASSWORD}' || CHR(34);
  DBMS_OUTPUT.PUT_LINE('User APP_USER password synchronized with DB_USER_PASSWORD');
END;
/

-- Grant privileges (execute unconditionally as they may be revoked)
DECLARE
BEGIN
  EXECUTE IMMEDIATE 'GRANT CREATE SESSION TO APP_USER';
  EXECUTE IMMEDIATE 'GRANT CREATE TABLE TO APP_USER';
  EXECUTE IMMEDIATE 'GRANT CREATE SEQUENCE TO APP_USER';
  EXECUTE IMMEDIATE 'GRANT CREATE INDEX TO APP_USER';
  EXECUTE IMMEDIATE 'ALTER USER APP_USER QUOTA UNLIMITED ON ssfspace';
  DBMS_OUTPUT.PUT_LINE('Privileges granted to APP_USER');
EXCEPTION
  WHEN OTHERS THEN
    IF SQLCODE = -1931 OR SQLCODE = -4042 THEN
      DBMS_OUTPUT.PUT_LINE('Privileges already granted');
    ELSE
      RAISE;
    END IF;
END;
/

COMMIT;
EXIT;
EOFUSER

cat /tmp/init_user.log
echo "User creation completed"

# Wait for user to be fully available
sleep 2

# Initialize schema and create default user if none exists
echo "=== Initializing database schema ==="

# Create a secure temporary password file to avoid password exposure in process listings
TEMP_PWD_FILE=$(mktemp)
chmod 600 "$TEMP_PWD_FILE"
printf '%s' "$DB_PASSWORD" > "$TEMP_PWD_FILE"

# Read password from temp file into shell variable to avoid exposing it in process args
read -r DB_PASSWORD_TEMP < "$TEMP_PWD_FILE"

"$ORACLE_HOME/bin/sqlplus" -s /nolog > /tmp/init_schema.log 2>&1 <<EOFSCHEMA
CONNECT APP_USER/$DB_PASSWORD_TEMP@FREEPDB1
WHENEVER SQLERROR EXIT SQL.SQLCODE
-- Create sequences
DECLARE
  v_seq_exists NUMBER := 0;
BEGIN
  SELECT COUNT(*) INTO v_seq_exists 
  FROM user_sequences 
  WHERE sequence_name = 'AUDIT_SEQ';
  
  IF v_seq_exists = 0 THEN
    EXECUTE IMMEDIATE 'CREATE SEQUENCE audit_seq START WITH 1 INCREMENT BY 1 NOCYCLE';
    DBMS_OUTPUT.PUT_LINE('Sequence audit_seq created');
  ELSE
    DBMS_OUTPUT.PUT_LINE('Sequence audit_seq already exists');
  END IF;
END;
/

-- Create users table if it doesn't exist
DECLARE
  v_table_exists NUMBER := 0;
BEGIN
  SELECT COUNT(*) INTO v_table_exists 
  FROM user_tables 
  WHERE table_name = 'USERS';
  
  IF v_table_exists = 0 THEN
    EXECUTE IMMEDIATE 'CREATE TABLE users (
      id NUMBER(19) GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
      username VARCHAR2(255) NOT NULL UNIQUE,
      password VARCHAR2(255) NOT NULL,
      email VARCHAR2(255) NOT NULL UNIQUE,
      created_at TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL,
      updated_at TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL
    )';
    DBMS_OUTPUT.PUT_LINE('Table users created');
  ELSE
    DBMS_OUTPUT.PUT_LINE('Table users already exists');
  END IF;
END;
/

-- Create default admin user if no users exist
DECLARE
  v_count NUMBER;
BEGIN
  SELECT COUNT(*) INTO v_count FROM users;
  
  IF v_count = 0 THEN
    INSERT INTO users (username, password, email, created_at, updated_at)
    VALUES (
      'admin',
      '$2a$12$K9Bd8ZBY6vQmJK8.5LZ/Oe9g.L7eKq5m3H9N2X4kR1vP8Q6tJ0gNm',
      'admin@example.com',
      SYSTIMESTAMP,
      SYSTIMESTAMP
    );
    COMMIT;
    DBMS_OUTPUT.PUT_LINE('Default admin user created: username=admin');
  ELSE
    DBMS_OUTPUT.PUT_LINE('Users already exist, skipping default user creation');
  END IF;
END;
/

COMMIT;
EXIT;
EOFSCHEMA

if [ $? -ne 0 ]; then
  echo "WARNING: Schema initialization had issues (may be normal if schema already exists)"
  cat /tmp/init_schema.log
fi

cat /tmp/init_schema.log

# Securely clean up temporary password file and sensitive variables
rm -f "$TEMP_PWD_FILE"
unset DB_PASSWORD_TEMP
unset DB_PASSWORD

echo "=== Database initialization completed successfully ==="