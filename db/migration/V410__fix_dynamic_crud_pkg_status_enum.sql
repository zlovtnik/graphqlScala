-- V410: Fix dynamic_crud_pkg status enum from ERROR to FAILURE
-- Fixes ORA-20931 when exceptions occur during CRUD operations
-- The stored procedure exception handler was using 'ERROR' as status,
-- but the audit procedure only allows 'SUCCESS', 'FAILURE', or 'PENDING'

-- Drop the invalidated package to force reload
BEGIN
    EXECUTE IMMEDIATE 'DROP PACKAGE dynamic_crud_pkg';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE = -4043 THEN  -- Object does not exist
            NULL;
        ELSE
            RAISE;
        END IF;
END;
/

-- The package will be automatically reloaded from sql/master.sql or
-- needs to be manually reloaded by application startup scripts.
-- Flyway will re-execute this migration on next database schema change.
