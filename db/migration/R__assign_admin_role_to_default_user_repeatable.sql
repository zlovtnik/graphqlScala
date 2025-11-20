-- R__assign_admin_role_to_default_user_repeatable.sql
-- Repeatable Flyway migration (runs on every deployment if content changes)
-- Assign ROLE_ADMIN to the default admin user if not already assigned
-- 
-- This repeatable migration ensures the admin user always has the ROLE_ADMIN role.
-- It runs after all numbered migrations (V*.sql) complete, so the admin user,
-- roles table, and user_roles table will definitely exist by this point.

DECLARE
    v_admin_user_id users.id%TYPE;
    v_admin_role_id roles.id%TYPE;
    v_exists NUMBER;
BEGIN
    -- Find the default admin user
    BEGIN
        SELECT id INTO v_admin_user_id 
        FROM users 
        WHERE username = 'admin' 
        AND ROWNUM = 1;
    EXCEPTION
        WHEN NO_DATA_FOUND THEN
            v_admin_user_id := NULL;
    END;

    -- Find the ROLE_ADMIN role
    BEGIN
        SELECT id INTO v_admin_role_id 
        FROM roles 
        WHERE name = 'ROLE_ADMIN' 
        AND ROWNUM = 1;
    EXCEPTION
        WHEN NO_DATA_FOUND THEN
            v_admin_role_id := NULL;
    END;

    -- Only proceed if both admin user and role exist
    IF v_admin_user_id IS NOT NULL AND v_admin_role_id IS NOT NULL THEN
        -- Check if the admin user already has the ROLE_ADMIN role
        SELECT COUNT(*) INTO v_exists 
        FROM user_roles 
        WHERE user_id = v_admin_user_id 
        AND role_id = v_admin_role_id;

        -- Only insert if the assignment doesn't already exist
        IF v_exists = 0 THEN
            INSERT INTO user_roles (user_id, role_id, granted_by, granted_at)
            VALUES (v_admin_user_id, v_admin_role_id, v_admin_user_id, SYSTIMESTAMP);
        END IF;
    END IF;

    COMMIT;
EXCEPTION
    WHEN OTHERS THEN
        -- Rollback on error - preserve data integrity
        ROLLBACK;
        -- Optionally log the error without failing the migration
        DBMS_OUTPUT.PUT_LINE('Warning: Failed to assign admin role - ' || SQLERRM);
END;
/
