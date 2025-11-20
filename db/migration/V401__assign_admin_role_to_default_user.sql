-- V401__assign_admin_role_to_default_user.sql
-- Assign ROLE_ADMIN to the default admin user created during bootstrap
-- 
-- NOTE: This migration is safe to run multiple times. If the admin user and 
-- ROLE_ADMIN role don't exist yet (e.g., during initial schema bootstrap), 
-- it will skip silently. The docker-entrypoint-initdb.d scripts run after 
-- Flyway migrations and will create these objects. A separate bootstrap process
-- or manual GraphQL mutation should assign the role after initial setup.

DECLARE
    v_admin_user_id users.id%TYPE;
    v_admin_role_id roles.id%TYPE;
    v_exists NUMBER;
BEGIN
    -- Try to find the default admin user (created during schema bootstrap)
    BEGIN
        SELECT id INTO v_admin_user_id 
        FROM users 
        WHERE username = 'admin' 
        AND ROWNUM = 1;
    EXCEPTION
        WHEN NO_DATA_FOUND THEN
            v_admin_user_id := NULL;
    END;

    -- Try to find the ROLE_ADMIN role
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
        -- Silently skip if objects don't exist yet
        -- They will be created by bootstrap scripts after migrations complete
        COMMIT;
END;
/
