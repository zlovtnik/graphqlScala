-- Create user_roles junction table with temporal support
BEGIN
    EXECUTE IMMEDIATE 'CREATE TABLE user_roles (
        id NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
        user_id NUMBER(19) NOT NULL,
        role_id NUMBER NOT NULL,
        granted_by NUMBER(19),
        granted_at TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
        expires_at TIMESTAMP(6) WITH TIME ZONE,
        CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
        CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE CASCADE,
        CONSTRAINT fk_user_roles_granted_by FOREIGN KEY (granted_by) REFERENCES users(id) ON DELETE SET NULL,
        CONSTRAINT uq_user_role UNIQUE (user_id, role_id)
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
