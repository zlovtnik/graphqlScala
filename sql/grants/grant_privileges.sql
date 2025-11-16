-- Grant necessary privileges to the application user
-- Run this as SYS or DBA user before executing the schema

-- Replace 'APP_USER' with your actual Oracle username if different
GRANT CREATE TABLE TO APP_USER;
GRANT CREATE PROCEDURE TO APP_USER;
GRANT CREATE SEQUENCE TO APP_USER;
GRANT CREATE TRIGGER TO APP_USER;
GRANT CREATE TYPE TO APP_USER;
GRANT EXECUTE ON DBMS_CRYPTO TO APP_USER;
GRANT UNLIMITED TABLESPACE TO APP_USER;

-- If you need to drop and recreate, also grant DROP ANY TABLE, etc., but be careful
-- GRANT DROP ANY TABLE TO APP_USER;
-- GRANT ALTER ANY TABLE TO APP_USER;