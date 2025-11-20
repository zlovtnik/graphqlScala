-- V400__seed_default_roles.sql
-- Seed default roles into the roles table
-- Uses Oracle-compatible MERGE statements for idempotent inserts

BEGIN
    -- Insert ROLE_USER if not exists
    MERGE INTO roles r
    USING (SELECT 'ROLE_USER' AS name, 'Standard user with basic access' AS description FROM DUAL) src
    ON (r.name = src.name)
    WHEN NOT MATCHED THEN
        INSERT (name, description, created_at, updated_at)
        VALUES (src.name, src.description, SYSDATE, SYSDATE);
    
    -- Insert ROLE_ADMIN if not exists
    MERGE INTO roles r
    USING (SELECT 'ROLE_ADMIN' AS name, 'Administrator with management privileges' AS description FROM DUAL) src
    ON (r.name = src.name)
    WHEN NOT MATCHED THEN
        INSERT (name, description, created_at, updated_at)
        VALUES (src.name, src.description, SYSDATE, SYSDATE);
    
    -- Insert ROLE_SUPER_ADMIN if not exists
    MERGE INTO roles r
    USING (SELECT 'ROLE_SUPER_ADMIN' AS name, 'Super administrator with full system access' AS description FROM DUAL) src
    ON (r.name = src.name)
    WHEN NOT MATCHED THEN
        INSERT (name, description, created_at, updated_at)
        VALUES (src.name, src.description, SYSDATE, SYSDATE);
    
    -- Insert ROLE_MFA_ADMIN if not exists
    MERGE INTO roles r
    USING (SELECT 'ROLE_MFA_ADMIN' AS name, 'MFA administrator for credential management' AS description FROM DUAL) src
    ON (r.name = src.name)
    WHEN NOT MATCHED THEN
        INSERT (name, description, created_at, updated_at)
        VALUES (src.name, src.description, SYSDATE, SYSDATE);
    
    COMMIT;
EXCEPTION
    WHEN OTHERS THEN
        ROLLBACK;
        RAISE;
END;
/
