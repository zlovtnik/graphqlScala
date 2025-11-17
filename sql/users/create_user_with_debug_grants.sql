-- Debug-only privileges for local development
--
-- IMPORTANT: This script must be executed MANUALLY by a DBA with SYS/DBA privileges
-- AFTER running create_user_with_grants.sql, and ONLY in local/dev environments.
--
-- Run this script as SYS or DBA user:
--   sqlplus / as sysdba
--   SQL> @sql/users/create_user_with_debug_grants.sql
--
-- NEVER apply these grants in staging, QA, or production without explicit DBA approval.

GRANT DEBUG CONNECT SESSION TO APP_USER;
GRANT DEBUG ANY PROCEDURE TO APP_USER;

COMMIT;
