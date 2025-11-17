#!/bin/bash

echo "=== Creating additional audit tables ==="

DB_PASSWORD=${DB_USER_PASSWORD:-APP_USER}

# Create audit tables
$ORACLE_HOME/bin/sqlplus -L /nolog > /tmp/init_audit.log 2>&1 <<EOFAUDIT
CONNECT APP_USER/"$DB_PASSWORD"@FREEPDB1;
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
    id NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id NUMBER(19) NOT NULL,
    token_hash VARCHAR2(255) NOT NULL,
    ip_address VARCHAR2(45),
    user_agent VARCHAR2(2000),
    created_at TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT fk_audit_sessions_user_id FOREIGN KEY (user_id) REFERENCES users(id)
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
    id NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    error_code VARCHAR2(50),
    error_message VARCHAR2(4000),
    context VARCHAR2(4000),
    procedure_name VARCHAR2(128),
    stack_trace CLOB,
    user_id NUMBER(19),
    session_id NUMBER(19),
    error_level VARCHAR2(20),
    created_at TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT chk_audit_error_log_level CHECK (error_level IN (''INFO'', ''WARN'', ''ERROR'', ''CRITICAL'')),
    CONSTRAINT fk_audit_error_log_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL,
    CONSTRAINT fk_audit_error_log_session FOREIGN KEY (session_id) REFERENCES audit_sessions(id) ON DELETE SET NULL
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
    id NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    operation VARCHAR2(50) NOT NULL,
    entity_type VARCHAR2(255) NOT NULL,
    entity_id NUMBER(19),
    user_id NUMBER(19),
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
