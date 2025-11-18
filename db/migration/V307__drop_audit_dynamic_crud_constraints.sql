-- Note: V306 intentionally omitted CHECK constraints from audit_dynamic_crud table recreation.
-- Validation of operation (INSERT|UPDATE|DELETE|SELECT) and status (SUCCESS|FAILURE|PENDING) is
-- enforced at the procedure level in record_audit, not at table constraints.
-- This migration attempts to clean up any legacy constraints that may have existed before V306.
BEGIN
    DECLARE
        v_constraint_name VARCHAR2(30);
    BEGIN
        -- Drop CHK_AUDIT_DYNAMIC_CRUD_STATUS if it exists
        SELECT constraint_name INTO v_constraint_name
        FROM user_constraints
        WHERE constraint_name = 'CHK_AUDIT_DYNAMIC_CRUD_STATUS'
        AND table_name = 'AUDIT_DYNAMIC_CRUD';
        
        BEGIN
            EXECUTE IMMEDIATE 'ALTER TABLE audit_dynamic_crud DROP CONSTRAINT ' || v_constraint_name;
            DBMS_OUTPUT.PUT_LINE('Dropped constraint ' || v_constraint_name);
        EXCEPTION
            WHEN OTHERS THEN
                DBMS_OUTPUT.PUT_LINE('Failed to drop constraint ' || v_constraint_name || ': ' || SQLERRM);
                RAISE;
        END;
    EXCEPTION
        WHEN NO_DATA_FOUND THEN
            NULL; -- Constraint does not exist, continue
    END;
    
    DECLARE
        v_constraint_name VARCHAR2(30);
    BEGIN
        -- Drop CHK_AUDIT_DYNAMIC_CRUD_OPERATION if it exists
        SELECT constraint_name INTO v_constraint_name
        FROM user_constraints
        WHERE constraint_name = 'CHK_AUDIT_DYNAMIC_CRUD_OPERATION'
        AND table_name = 'AUDIT_DYNAMIC_CRUD';
        
        BEGIN
            EXECUTE IMMEDIATE 'ALTER TABLE audit_dynamic_crud DROP CONSTRAINT ' || v_constraint_name;
            DBMS_OUTPUT.PUT_LINE('Dropped constraint ' || v_constraint_name);
        EXCEPTION
            WHEN OTHERS THEN
                DBMS_OUTPUT.PUT_LINE('Failed to drop constraint ' || v_constraint_name || ': ' || SQLERRM);
                RAISE;
        END;
    EXCEPTION
        WHEN NO_DATA_FOUND THEN
            NULL; -- Constraint does not exist, continue
    END;
END;
/
