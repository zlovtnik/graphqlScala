-- Flyway-style migration: enforce local compressed indexes on partitioned audit tables
SET SERVEROUTPUT ON
DECLARE
    PROCEDURE ensure_index(p_name VARCHAR2, p_sql VARCHAR2) IS
    BEGIN
        EXECUTE IMMEDIATE p_sql;
        DBMS_OUTPUT.PUT_LINE('Created index ' || p_name);
    EXCEPTION
        WHEN OTHERS THEN
            IF SQLCODE = -955 THEN
                DBMS_OUTPUT.PUT_LINE('Index ' || p_name || ' already exists');
            ELSE
                RAISE;
            END IF;
    END;
BEGIN
    ensure_index('IDX_AUDIT_DYNAMIC_CRUD_TABLE_NAME',
                 'CREATE INDEX idx_audit_dynamic_crud_table_name ON audit_dynamic_crud(table_name) LOCAL COMPRESS ADVANCED LOW');
    ensure_index('IDX_AUDIT_DYNAMIC_CRUD_OPERATION',
                 'CREATE INDEX idx_audit_dynamic_crud_operation ON audit_dynamic_crud(operation) LOCAL COMPRESS ADVANCED LOW');
    ensure_index('IDX_AUDIT_DYNAMIC_CRUD_CREATED_AT',
                 'CREATE INDEX idx_audit_dynamic_crud_created_at ON audit_dynamic_crud(created_at) LOCAL COMPRESS ADVANCED LOW');
    ensure_index('IDX_AUDIT_DYNAMIC_CRUD_ACTOR',
                 'CREATE INDEX idx_audit_dynamic_crud_actor ON audit_dynamic_crud(actor) LOCAL COMPRESS ADVANCED LOW');
    ensure_index('IDX_AUDIT_DYNAMIC_CRUD_STATUS_CREATED',
                 'CREATE INDEX idx_audit_dynamic_crud_status_created ON audit_dynamic_crud(status, created_at) LOCAL COMPRESS ADVANCED LOW');

    ensure_index('IDX_AUDIT_LOGIN_ATTEMPTS_USERNAME',
                 'CREATE INDEX idx_audit_login_attempts_username ON audit_login_attempts(username) LOCAL COMPRESS ADVANCED LOW');
    ensure_index('IDX_AUDIT_LOGIN_ATTEMPTS_SUCCESS',
                 'CREATE INDEX idx_audit_login_attempts_success ON audit_login_attempts(success) LOCAL COMPRESS ADVANCED LOW');
    ensure_index('IDX_AUDIT_LOGIN_ATTEMPTS_CREATED_AT',
                 'CREATE INDEX idx_audit_login_attempts_created_at ON audit_login_attempts(created_at) LOCAL COMPRESS ADVANCED LOW');

    ensure_index('IDX_AUDIT_SESSIONS_USER_ID',
                 'CREATE INDEX idx_audit_sessions_user_id ON audit_sessions(user_id) LOCAL COMPRESS ADVANCED LOW');
    ensure_index('IDX_AUDIT_SESSIONS_CREATED_AT',
                 'CREATE INDEX idx_audit_sessions_created_at ON audit_sessions(created_at) LOCAL COMPRESS ADVANCED LOW');

    ensure_index('IDX_AUDIT_ERROR_LOG_ERROR_CODE',
                 'CREATE INDEX idx_audit_error_log_error_code ON audit_error_log(error_code) LOCAL COMPRESS ADVANCED LOW');
    ensure_index('IDX_AUDIT_ERROR_LOG_CREATED_AT',
                 'CREATE INDEX idx_audit_error_log_created_at ON audit_error_log(created_at) LOCAL COMPRESS ADVANCED LOW');
END;
/
