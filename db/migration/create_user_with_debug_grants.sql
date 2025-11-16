-- Debug-only privileges for local development
-- Run AFTER create_user_with_grants.sql when working in local/dev environments only.
-- NEVER apply these grants in staging, QA, or production without explicit DBA approval.

GRANT DEBUG CONNECT SESSION TO app_user;
GRANT DEBUG ANY PROCEDURE TO app_user;

COMMIT;
