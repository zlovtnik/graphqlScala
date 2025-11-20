-- Create audit_role_changes table for role audit logging
BEGIN
    EXECUTE IMMEDIATE 'CREATE TABLE audit_role_changes (
        id NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
        user_id NUMBER(19) NOT NULL,
        role_name VARCHAR2(50) NOT NULL,
        action VARCHAR2(20) NOT NULL,
        performed_by NUMBER(19),
        reason VARCHAR2(500),
        ip_address VARCHAR2(45),
        created_at TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
        CONSTRAINT fk_audit_role_changes_user FOREIGN KEY (user_id) REFERENCES users(id),
        CONSTRAINT fk_audit_role_changes_performed_by FOREIGN KEY (performed_by) REFERENCES users(id),
        CONSTRAINT chk_audit_role_action CHECK (action IN (''GRANT'', ''REVOKE'', ''EXPIRE''))
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
