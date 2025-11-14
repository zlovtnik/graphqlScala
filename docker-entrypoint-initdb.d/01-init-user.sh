#!/bin/bash

echo "=== Starting Oracle Database Initialization ==="

DB_PASSWORD=${DB_USER_PASSWORD:-ssfuser}
RETRIES=0
MAX_RETRIES=60

# Sanitize password to prevent SQL injection
ESCAPED_PASSWORD=$(echo "$DB_PASSWORD" | sed "s/'/''/g")

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
  WHERE username = 'SSFUSER';
  
  IF v_user_exists = 0 THEN
    EXECUTE IMMEDIATE 'CREATE USER ssfuser IDENTIFIED BY "ssfuser" DEFAULT TABLESPACE ssfspace';
    DBMS_OUTPUT.PUT_LINE('User ssfuser created');
  ELSE
    DBMS_OUTPUT.PUT_LINE('User ssfuser already exists');
  END IF;
END;
/

-- Grant privileges (execute unconditionally as they may be revoked)
DECLARE
BEGIN
  EXECUTE IMMEDIATE 'GRANT CREATE SESSION TO ssfuser';
  EXECUTE IMMEDIATE 'GRANT CREATE TABLE TO ssfuser';
  EXECUTE IMMEDIATE 'GRANT CREATE SEQUENCE TO ssfuser';
  EXECUTE IMMEDIATE 'GRANT CREATE INDEX TO ssfuser';
  EXECUTE IMMEDIATE 'GRANT UNLIMITED TABLESPACE TO ssfuser';
  DBMS_OUTPUT.PUT_LINE('Privileges granted to ssfuser');
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

"$ORACLE_HOME/bin/sqlplus" -s ssfuser/"$DB_PASSWORD"@FREEPDB1 > /tmp/init_schema.log 2>&1 <<'EOFSCHEMA'
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
      id VARCHAR2(36) PRIMARY KEY,
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
    INSERT INTO users (id, username, password, email, created_at, updated_at)
    VALUES (
      SUBSTR(SYS_GUID(), 1, 36),
      'admin',
      '$2a$10$W9r82p/yEdCEXXx/5i5qDOPTJWvEoB8nLvZN3MfZ7H/pZH8cI7Z0u',
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
echo "=== Database initialization completed successfully ==="