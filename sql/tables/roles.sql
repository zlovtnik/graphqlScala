-- Create roles table
-- Predefined RBAC roles: USER, ADMIN, SUPER_ADMIN, MFA_ADMIN
BEGIN
    EXECUTE IMMEDIATE 'CREATE TABLE roles (
        id NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
        name VARCHAR2(50) NOT NULL UNIQUE,
        description VARCHAR2(255),
        created_at TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
        updated_at TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
        CONSTRAINT chk_role_name CHECK (name IN (''ROLE_USER'', ''ROLE_ADMIN'', ''ROLE_SUPER_ADMIN'', ''ROLE_MFA_ADMIN''))
    )';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE = -955 THEN
            NULL;  -- Table already exists, continue
        ELSE
            RAISE;
        END IF;
END;
/
