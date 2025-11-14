-- Master SQL script to set up the Oracle schema
-- Run sequences, types, tables, and packages as the application user (e.g., ssfuser)
-- Run users and grants scripts separately as SYS or DBA user

-- Sequences (run as app user)
@@sequences/audit_seq.sql

-- Types (run as app user)
@@types/dynamic_types.sql

-- Tables (run as app user)
@@tables/users.sql
@@tables/audit_dynamic_crud.sql
@@tables/audit_login_attempts.sql
@@tables/audit_sessions.sql
@@tables/audit_error_log.sql

-- Indexes (run as app user)
@@indexes/users_indexes.sql
@@indexes/audit_dynamic_crud_indexes.sql

-- Packages (run as app user)
@@packages/dynamic_crud_pkg_spec.sql
@@packages/dynamic_crud_pkg_body.sql
@@packages/user_pkg_spec.sql
@@packages/user_pkg_body.sql

-- Users and Grants (run these separately as SYS/DBA)
-- @@users/create_user_with_grants.sql
-- @@users/create_user_with_debug_grants.sql
-- @@grants/grant_privileges.sql