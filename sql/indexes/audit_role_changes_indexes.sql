-- Create indexes for audit_role_changes table
BEGIN
    EXECUTE IMMEDIATE 'CREATE INDEX idx_audit_role_changes_user_id ON audit_role_changes(user_id)';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE IN (-955, -1408) THEN NULL;
        ELSE RAISE;
        END IF;
END;
/

BEGIN
    EXECUTE IMMEDIATE 'CREATE INDEX idx_audit_role_changes_created_at ON audit_role_changes(created_at)';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE IN (-955, -1408) THEN NULL;
        ELSE RAISE;
        END IF;
END;
/

BEGIN
    EXECUTE IMMEDIATE 'CREATE INDEX idx_audit_role_changes_performed_by ON audit_role_changes(performed_by)';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE IN (-955, -1408) THEN NULL;
        ELSE RAISE;
        END IF;
END;
/
