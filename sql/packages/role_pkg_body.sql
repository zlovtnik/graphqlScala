-- role_pkg_body.sql
-- Package body for role management procedures and functions

CREATE OR REPLACE PACKAGE BODY role_pkg AS
    
    PROCEDURE grant_role(
        p_user_id IN users.id%TYPE,
        p_role_name IN VARCHAR2,
        p_granted_by IN users.id%TYPE,
        p_expires_at IN TIMESTAMP DEFAULT NULL,
        p_ip_address IN VARCHAR2 DEFAULT NULL
    ) IS
        v_role_id roles.id%TYPE;
        v_count NUMBER;
    BEGIN
        -- Validate role exists
        BEGIN
            SELECT id INTO v_role_id FROM roles WHERE name = p_role_name;
        EXCEPTION
            WHEN NO_DATA_FOUND THEN
                RAISE_APPLICATION_ERROR(-20001, 'Role not found: ' || p_role_name);
        END;
        
        -- Check if user already has role
        SELECT COUNT(*) INTO v_count FROM user_roles 
        WHERE user_id = p_user_id AND role_id = v_role_id 
        AND (expires_at IS NULL OR expires_at > SYSTIMESTAMP);
        
        IF v_count > 0 THEN
            RAISE_APPLICATION_ERROR(-20002, 'User already has this role');
        END IF;
        
        -- Grant role
        INSERT INTO user_roles (user_id, role_id, granted_by, granted_at, expires_at)
        VALUES (p_user_id, v_role_id, p_granted_by, SYSTIMESTAMP, p_expires_at);
        
        -- Audit log
        INSERT INTO audit_role_changes (user_id, role_name, action, performed_by, ip_address, created_at)
        VALUES (p_user_id, p_role_name, 'GRANT', p_granted_by, p_ip_address, SYSTIMESTAMP);
        
        -- Transaction management is handled by Java @Transactional annotation
    END grant_role;
    
    PROCEDURE revoke_role(
        p_user_id IN users.id%TYPE,
        p_role_name IN VARCHAR2,
        p_revoked_by IN users.id%TYPE,
        p_reason IN VARCHAR2 DEFAULT NULL,
        p_ip_address IN VARCHAR2 DEFAULT NULL
    ) IS
        v_role_id roles.id%TYPE;
        v_count NUMBER;
    BEGIN
        -- Validate role exists
        BEGIN
            SELECT id INTO v_role_id FROM roles WHERE name = p_role_name;
        EXCEPTION
            WHEN NO_DATA_FOUND THEN
                RAISE_APPLICATION_ERROR(-20001, 'Role not found: ' || p_role_name);
        END;
        
        -- Check if user has role
        SELECT COUNT(*) INTO v_count FROM user_roles 
        WHERE user_id = p_user_id AND role_id = v_role_id;
        
        IF v_count = 0 THEN
            RAISE_APPLICATION_ERROR(-20003, 'User does not have this role');
        END IF;
        
        -- Revoke role
        DELETE FROM user_roles 
        WHERE user_id = p_user_id AND role_id = v_role_id;
        
        -- Audit log
        INSERT INTO audit_role_changes (user_id, role_name, action, performed_by, reason, ip_address, created_at)
        VALUES (p_user_id, p_role_name, 'REVOKE', p_revoked_by, p_reason, p_ip_address, SYSTIMESTAMP);
        
        -- Transaction management is handled by Java @Transactional annotation
    END revoke_role;
    
    FUNCTION get_user_roles(p_user_id IN users.id%TYPE) RETURN SYS_REFCURSOR IS
        v_cursor SYS_REFCURSOR;
    BEGIN
        OPEN v_cursor FOR
            SELECT r.id, r.name, r.description, r.created_at, r.updated_at, 
                   ur.granted_at, ur.expires_at, ur.granted_by
            FROM roles r
            INNER JOIN user_roles ur ON r.id = ur.role_id
            WHERE ur.user_id = p_user_id
            AND (ur.expires_at IS NULL OR ur.expires_at > SYSTIMESTAMP)
            ORDER BY r.name;
        
        RETURN v_cursor;
    END get_user_roles;
    
    FUNCTION has_role(
        p_user_id IN users.id%TYPE,
        p_role_name IN VARCHAR2
    ) RETURN BOOLEAN IS
        v_count NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_count
        FROM user_roles ur
        INNER JOIN roles r ON ur.role_id = r.id
        WHERE ur.user_id = p_user_id
        AND r.name = p_role_name
        AND (ur.expires_at IS NULL OR ur.expires_at > SYSTIMESTAMP);
        
        RETURN v_count > 0;
    END has_role;
    PROCEDURE expire_roles IS
        v_error_count NUMBER := 0;
    BEGIN
        -- Mark roles as expired and create audit entries
        FOR role_rec IN (
            SELECT ur.id, ur.user_id, r.name, ur.granted_by
            FROM user_roles ur
            INNER JOIN roles r ON ur.role_id = r.id
            WHERE ur.expires_at <= SYSTIMESTAMP
        ) LOOP
            BEGIN
                DELETE FROM user_roles WHERE id = role_rec.id;
                
                INSERT INTO audit_role_changes (user_id, role_name, action, performed_by, created_at)
                VALUES (role_rec.user_id, role_rec.name, 'EXPIRE', role_rec.granted_by, SYSTIMESTAMP);
            EXCEPTION
                WHEN OTHERS THEN
                    v_error_count := v_error_count + 1;
                    -- Log error but continue processing
            END;
        END LOOP;
    END expire_roles;
    FUNCTION get_role_audit_log(
        p_user_id IN users.id%TYPE DEFAULT NULL,
        p_limit IN NUMBER DEFAULT 100,
        p_offset IN NUMBER DEFAULT 0
    ) RETURN SYS_REFCURSOR IS
        v_cursor SYS_REFCURSOR;
    BEGIN
        OPEN v_cursor FOR
            SELECT arc.id, arc.user_id, arc.role_name, arc.action, 
                   arc.performed_by, arc.reason, arc.ip_address, arc.created_at
            FROM audit_role_changes arc
            WHERE (p_user_id IS NULL OR arc.user_id = p_user_id)
            ORDER BY arc.created_at DESC
            OFFSET p_offset ROWS FETCH NEXT p_limit ROWS ONLY;
        
        RETURN v_cursor;
    END get_role_audit_log;
    END get_role_audit_log;
    
END role_pkg;
/
