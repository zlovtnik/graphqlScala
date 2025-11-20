-- V399__create_roles_schema.sql
-- Create roles, user_roles, and audit_role_changes tables

-- Create roles table
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
            NULL;
        ELSE
            RAISE;
        END IF;
END;
/

-- Create user_roles junction table
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
            NULL;
        ELSE
            RAISE;
        END IF;
END;
/

-- Create indexes for user_roles
BEGIN
    EXECUTE IMMEDIATE 'CREATE INDEX idx_user_roles_user_id ON user_roles(user_id)';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE = -955 THEN NULL;
        ELSE RAISE;
        END IF;
END;
/

BEGIN
    EXECUTE IMMEDIATE 'CREATE INDEX idx_user_roles_role_id ON user_roles(role_id)';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE = -955 THEN NULL;
        ELSE RAISE;
        END IF;
END;
/

BEGIN
    EXECUTE IMMEDIATE 'CREATE INDEX idx_user_roles_expires_at ON user_roles(expires_at)';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE = -955 THEN NULL;
        ELSE RAISE;
        END IF;
END;
/

-- Create audit_role_changes table
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
        CONSTRAINT fk_audit_role_changes_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL,
        CONSTRAINT fk_audit_role_changes_performed_by FOREIGN KEY (performed_by) REFERENCES users(id) ON DELETE SET NULL,
        CONSTRAINT chk_audit_role_action CHECK (action IN (''GRANT'', ''REVOKE'', ''EXPIRE''))
    )';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE = -955 THEN
            NULL;
        ELSE
            RAISE;
        END IF;
END;
/

-- Create indexes for audit_role_changes
BEGIN
    EXECUTE IMMEDIATE 'CREATE INDEX idx_audit_role_changes_user_id ON audit_role_changes(user_id)';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE = -955 THEN NULL;
        ELSE RAISE;
        END IF;
END;
/

BEGIN
    EXECUTE IMMEDIATE 'CREATE INDEX idx_audit_role_changes_created_at ON audit_role_changes(created_at)';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE = -955 THEN NULL;
        ELSE RAISE;
        END IF;
END;
/

BEGIN
    EXECUTE IMMEDIATE 'CREATE INDEX idx_audit_role_changes_performed_by ON audit_role_changes(performed_by)';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE = -955 THEN NULL;
        ELSE RAISE;
        END IF;
END;
/
