-- Create Oracle user with comprehensive privileges for development
-- Run this script as SYS or DBA user
-- WARNING: This grants extensive privileges. In production, use minimal required privileges only.

-- Create the application user
-- Replace '&PASSWORD' with a secure password when running the script
CREATE USER app_user IDENTIFIED BY &PASSWORD
DEFAULT TABLESPACE users
TEMPORARY TABLESPACE temp
QUOTA UNLIMITED ON users;

-- Grant basic connection privileges
GRANT CONNECT TO app_user;

-- Grant resource privileges for creating objects
GRANT RESOURCE TO app_user;

-- Additional specific grants for comprehensive access
GRANT CREATE SESSION TO app_user;
GRANT CREATE TABLE TO app_user;
GRANT CREATE VIEW TO app_user;
GRANT CREATE PROCEDURE TO app_user;  -- Covers procedures, functions, packages
GRANT CREATE SEQUENCE TO app_user;
GRANT CREATE TRIGGER TO app_user;
GRANT CREATE TYPE TO app_user;
GRANT CREATE SYNONYM TO app_user;
GRANT CREATE DATABASE LINK TO app_user;

-- System privileges
GRANT UNLIMITED TABLESPACE TO app_user;
GRANT EXECUTE ON DBMS_CRYPTO TO app_user;
GRANT EXECUTE ON DBMS_LOCK TO app_user;
GRANT EXECUTE ON DBMS_OUTPUT TO app_user;
GRANT EXECUTE ON UTL_RAW TO app_user;

-- For debugging and development
GRANT DEBUG CONNECT SESSION TO app_user;
GRANT DEBUG ANY PROCEDURE TO app_user;

-- Commit the changes
COMMIT;