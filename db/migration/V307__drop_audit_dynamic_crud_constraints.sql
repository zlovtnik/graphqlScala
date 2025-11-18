-- Drop any existing check constraints on audit_dynamic_crud if they exist
-- These constraints are too restrictive and conflict with the operations being used
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
            EXECUTE IMMEDIATE 'ALTER TABLE audit_dynamic_crud DROP CONSTRAINT "' || v_constraint_name || '"';
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
            EXECUTE IMMEDIATE 'ALTER TABLE audit_dynamic_crud DROP CONSTRAINT "' || v_constraint_name || '"';
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
