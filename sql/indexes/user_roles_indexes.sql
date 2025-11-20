-- Create indexes for user_roles table
BEGIN
    EXECUTE IMMEDIATE 'CREATE INDEX idx_user_roles_user_id ON user_roles(user_id)';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE IN (-955, -1408) THEN NULL;
        ELSE RAISE;
        END IF;
END;
/

BEGIN
    EXECUTE IMMEDIATE 'CREATE INDEX idx_user_roles_role_id ON user_roles(role_id)';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE IN (-955, -1408) THEN NULL;
        ELSE RAISE;
        END IF;
END;
/

BEGIN
    EXECUTE IMMEDIATE 'CREATE INDEX idx_user_roles_expires_at ON user_roles(expires_at)';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE IN (-955, -1408) THEN NULL;
        ELSE RAISE;
        END IF;
END;
/
