-- Grant necessary privileges to the application user
-- Run this as SYS or DBA user before executing the schema

-- Replace 'app_user' with your actual Oracle username
GRANT CREATE TABLE TO app_user;
GRANT CREATE PROCEDURE TO app_user;
GRANT CREATE SEQUENCE TO app_user;
GRANT CREATE TRIGGER TO app_user;
GRANT EXECUTE ON DBMS_CRYPTO TO app_user;
GRANT UNLIMITED TABLESPACE TO app_user;

-- If you need to drop and recreate, also grant DROP ANY TABLE, etc., but be careful
-- GRANT DROP ANY TABLE TO app_user;
-- GRANT ALTER ANY TABLE TO app_user;