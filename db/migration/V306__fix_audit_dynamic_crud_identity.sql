-- Fix: Ensure audit_dynamic_crud.id uses GENERATED ALWAYS AS IDENTITY
-- The original table definition used NUMBER PRIMARY KEY, but should use identity generation
-- This migration converts the table to use identity column if it doesn't already

BEGIN
    DECLARE
        v_table_exists NUMBER := 0;
        v_has_identity NUMBER := 0;
    BEGIN
        -- Check if table exists
        SELECT COUNT(*) INTO v_table_exists
        FROM user_tables
        WHERE table_name = 'AUDIT_DYNAMIC_CRUD';

        IF v_table_exists = 1 THEN
            -- Check if table already has IDENTITY column
            SELECT COUNT(*) INTO v_has_identity
            FROM user_tab_identity_cols
            WHERE table_name = 'AUDIT_DYNAMIC_CRUD' AND column_name = 'ID';

            IF v_has_identity = 0 THEN
                -- Table exists but doesn't have identity - need to recreate it
                EXECUTE IMMEDIATE 'ALTER TABLE audit_dynamic_crud RENAME TO audit_dynamic_crud_old';
                
                -- Create new table with identity column
                EXECUTE IMMEDIATE 'CREATE TABLE audit_dynamic_crud (
                    id NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                    table_name VARCHAR2(128) NOT NULL,
                    operation VARCHAR2(10) NOT NULL,
                    actor VARCHAR2(128),
                    trace_id VARCHAR2(128),
                    client_ip VARCHAR2(45),
                    metadata CLOB,
                    affected_rows NUMBER,
                    status VARCHAR2(20) NOT NULL,
                    message VARCHAR2(4000),
                    error_code VARCHAR2(50),
                    created_at TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL
                )';
                
                -- Copy data from old table
                EXECUTE IMMEDIATE 'INSERT INTO audit_dynamic_crud 
                    (table_name, operation, actor, trace_id, client_ip, metadata, affected_rows, status, message, error_code, created_at)
                    SELECT table_name, operation, actor, trace_id, client_ip, metadata, affected_rows, status, message, error_code, created_at
                    FROM audit_dynamic_crud_old';
                
                -- Drop old table
                EXECUTE IMMEDIATE 'DROP TABLE audit_dynamic_crud_old';
                
                DBMS_OUTPUT.PUT_LINE('Table audit_dynamic_crud converted to use GENERATED ALWAYS AS IDENTITY');
            ELSE
                DBMS_OUTPUT.PUT_LINE('Table audit_dynamic_crud already has GENERATED ALWAYS AS IDENTITY');
            END IF;
        ELSE
            DBMS_OUTPUT.PUT_LINE('Table audit_dynamic_crud does not exist - will be created by schema initialization');
        END IF;
    END;
END;
/
