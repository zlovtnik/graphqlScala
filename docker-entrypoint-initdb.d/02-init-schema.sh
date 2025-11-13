#!/bin/bash

echo "=== Creating additional audit tables ==="

DB_PASSWORD=${DB_USER_PASSWORD:-ssfuser}

# Create audit tables
$ORACLE_HOME/bin/sqlplus -L /nolog > /tmp/init_audit.log 2>&1 <<EOFAUDIT
CONNECT ssfuser/"$DB_PASSWORD"@FREEPDB1;
SET ECHO OFF FEEDBACK OFF PAGESIZE 0 LINESIZE 1000 HEADING OFF

-- Create audit_login_attempts table
BEGIN
  EXECUTE IMMEDIATE 'CREATE TABLE audit_login_attempts (
    id NUMBER GENERATED AS IDENTITY PRIMARY KEY,
    username VARCHAR2(255) NOT NULL,
    attempt_time TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL,
    success CHAR(1) NOT NULL,
    ip_address VARCHAR2(45)
  )';
  DBMS_OUTPUT.PUT_LINE('Table audit_login_attempts created');
EXCEPTION
  WHEN OTHERS THEN
    IF SQLCODE = -955 THEN
      DBMS_OUTPUT.PUT_LINE('Table audit_login_attempts already exists');
    ELSE
      RAISE;
    END IF;
END;
/

-- Create audit_sessions table
BEGIN
  EXECUTE IMMEDIATE 'CREATE TABLE audit_sessions (
    id VARCHAR2(36) PRIMARY KEY,
    user_id VARCHAR2(36) NOT NULL,
    created_at TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    last_activity TIMESTAMP DEFAULT SYSTIMESTAMP
  )';
  DBMS_OUTPUT.PUT_LINE('Table audit_sessions created');
EXCEPTION
  WHEN OTHERS THEN
    IF SQLCODE = -955 THEN
      DBMS_OUTPUT.PUT_LINE('Table audit_sessions already exists');
    ELSE
      RAISE;
    END IF;
END;
/

-- Create audit_error_log table
BEGIN
  EXECUTE IMMEDIATE 'CREATE TABLE audit_error_log (
    id NUMBER GENERATED AS IDENTITY PRIMARY KEY,
    error_message VARCHAR2(1000),
    stack_trace CLOB,
    created_at TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL
  )';
  DBMS_OUTPUT.PUT_LINE('Table audit_error_log created');
EXCEPTION
  WHEN OTHERS THEN
    IF SQLCODE = -955 THEN
      DBMS_OUTPUT.PUT_LINE('Table audit_error_log already exists');
    ELSE
      RAISE;
    END IF;
END;
/

-- Create audit_dynamic_crud table
BEGIN
  EXECUTE IMMEDIATE 'CREATE TABLE audit_dynamic_crud (
    id VARCHAR2(36) PRIMARY KEY,
    operation VARCHAR2(50) NOT NULL,
    entity_type VARCHAR2(255) NOT NULL,
    entity_id VARCHAR2(36),
    user_id VARCHAR2(36),
    changes CLOB,
    created_at TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL
  )';
  DBMS_OUTPUT.PUT_LINE('Table audit_dynamic_crud created');
EXCEPTION
  WHEN OTHERS THEN
    IF SQLCODE = -955 THEN
      DBMS_OUTPUT.PUT_LINE('Table audit_dynamic_crud already exists');
    ELSE
      RAISE;
    END IF;
END;
/

COMMIT;
EXIT;
EOFAUDIT

cat /tmp/init_audit.log
echo "=== Audit tables initialization completed ==="
