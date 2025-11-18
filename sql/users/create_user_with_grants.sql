-- Create Oracle user with production-safe baseline privileges
-- 
-- IMPORTANT: This script must be executed MANUALLY by a DBA with SYS/DBA privileges
-- BEFORE running any Flyway migrations. It cannot be run by Flyway (which runs as APP_USER).
--
-- Run this script as SYS or DBA user:
--   sqlplus / as sysdba
--   SQL> @sql/users/create_user_with_grants.sql
--
-- WARNING: This grants extensive privileges. In production, use minimal required privileges only.
-- Debug-only privileges have been moved to create_user_with_debug_grants.sql (dev/local use only).

-- IMPORTANT: For security, do NOT commit passwords in source control. Provide the password at runtime via a SQL*Plus/SQLcl substitution
-- Variable name: APP_USER_PASSWORD
-- Example: sqlplus / as sysdba @create_user_with_grants.sql "mypasswordHere"
-- Alternatively use an external secret manager or Oracle Wallet; if using a file, add it to .gitignore.
CREATE USER APP_USER IDENTIFIED BY &APP_USER_PASSWORD
DEFAULT TABLESPACE users
TEMPORARY TABLESPACE temp
QUOTA 10G ON users; -- Production default: enforce quota to catch runaway growth

-- Grant basic connection privileges
GRANT CONNECT TO APP_USER;

-- Grant resource privileges for creating objects
GRANT RESOURCE TO APP_USER;

-- Additional specific grants for comprehensive access
GRANT CREATE SESSION TO APP_USER;
GRANT CREATE TABLE TO APP_USER;
GRANT CREATE VIEW TO APP_USER;
GRANT CREATE PROCEDURE TO APP_USER;  -- Covers procedures, functions, packages
GRANT CREATE SEQUENCE TO APP_USER;
GRANT CREATE TRIGGER TO APP_USER;
GRANT CREATE TYPE TO APP_USER;
GRANT CREATE SYNONYM TO APP_USER;

-- System privileges (quota enforced above; do not grant unlimited tablespace by default)
GRANT EXECUTE ON DBMS_CRYPTO TO APP_USER;
GRANT EXECUTE ON DBMS_LOCK TO APP_USER;
GRANT EXECUTE ON DBMS_OUTPUT TO APP_USER;
GRANT EXECUTE ON UTL_RAW TO APP_USER;

-- Commit the changes
COMMIT;