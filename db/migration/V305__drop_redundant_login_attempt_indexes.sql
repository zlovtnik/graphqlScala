-- Flyway migration: remove redundant single-column indexes on audit_login_attempts and add optional composite index
SET SERVEROUTPUT ON;
DECLARE
    PROCEDURE safe_drop_index(p_index VARCHAR2) IS
    BEGIN
        EXECUTE IMMEDIATE 'DROP INDEX ' || p_index;
        DBMS_OUTPUT.PUT_LINE('Dropped index ' || p_index);
    EXCEPTION WHEN OTHERS THEN
        -- ignore if not present or other issues - keep migration idempotent
        DBMS_OUTPUT.PUT_LINE('Could not drop index ' || p_index || ' (it may not exist)');
    END;

    PROCEDURE ensure_index(p_index_sql VARCHAR2) IS
    BEGIN
        EXECUTE IMMEDIATE p_index_sql;
        DBMS_OUTPUT.PUT_LINE('Created index');
    EXCEPTION WHEN OTHERS THEN
        IF SQLCODE = -955 THEN
            DBMS_OUTPUT.PUT_LINE('Index already exists');
        ELSE
            RAISE;
        END IF;
    END;
BEGIN
    -- Drop the three single-column indexes that duplicate leading columns of composite indexes
    safe_drop_index('idx_audit_login_attempts_username');
    safe_drop_index('idx_audit_login_attempts_success');
    safe_drop_index('idx_audit_login_attempts_created_at');

    -- Optionally create composite index for IP+username+created_at to support efficient queries
    ensure_index('CREATE INDEX idx_audit_login_attempts_ip_username_created ON audit_login_attempts(ip_address, username, created_at) LOCAL COMPRESS ADVANCED LOW');
END;
/
