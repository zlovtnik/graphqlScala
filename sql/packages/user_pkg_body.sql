CREATE OR REPLACE PACKAGE BODY user_pkg AS
    PROCEDURE create_user(
        p_username IN VARCHAR2,
        p_password IN VARCHAR2,
        p_email IN VARCHAR2,
        p_user_id OUT users.id%TYPE
    ) IS
        v_count NUMBER;
    BEGIN
        -- Validate email format
        IF NOT REGEXP_LIKE(p_email, '^[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}$', 'i') THEN
            RAISE_APPLICATION_ERROR(-20005, 'Invalid email format');
        END IF;

        -- Validate password length
        IF LENGTH(p_password) < 8 THEN
            RAISE_APPLICATION_ERROR(-20006, 'Password must be at least 8 characters');
        END IF;

        -- Check if username already exists
        IF username_exists(p_username) THEN
            RAISE_APPLICATION_ERROR(-20001, 'Username already exists');
        END IF;

        -- Check if email already exists
        IF email_exists(p_email) THEN
            RAISE_APPLICATION_ERROR(-20002, 'Email already exists');
        END IF;

        -- Insert user (password is already hashed by application)
        INSERT INTO users (username, password, email, created_at, updated_at)
        VALUES (p_username, p_password, p_email, SYSTIMESTAMP, SYSTIMESTAMP)
        RETURNING id INTO p_user_id;

        COMMIT;
    EXCEPTION
        WHEN OTHERS THEN
            ROLLBACK;
            RAISE;
    END create_user;

    FUNCTION get_user_by_id(p_user_id IN users.id%TYPE) RETURN SYS_REFCURSOR IS
        v_cursor SYS_REFCURSOR;
    BEGIN
        OPEN v_cursor FOR
            SELECT id, username, email
            FROM users
            WHERE id = p_user_id;
        RETURN v_cursor;
    END get_user_by_id;

    FUNCTION get_user_by_username(p_username IN VARCHAR2) RETURN SYS_REFCURSOR IS
        v_cursor SYS_REFCURSOR;
    BEGIN
        OPEN v_cursor FOR
            SELECT id, username, email
            FROM users
            WHERE username = p_username;
        RETURN v_cursor;
    END get_user_by_username;

    FUNCTION get_user_by_email(p_email IN VARCHAR2) RETURN SYS_REFCURSOR IS
        v_cursor SYS_REFCURSOR;
    BEGIN
        OPEN v_cursor FOR
            SELECT id, username, email
            FROM users
            WHERE email = p_email;
        RETURN v_cursor;
    END get_user_by_email;

    PROCEDURE update_user(
        p_user_id IN users.id%TYPE,
        p_username IN VARCHAR2 DEFAULT NULL,
        p_email IN VARCHAR2 DEFAULT NULL,
        p_password IN VARCHAR2 DEFAULT NULL
    ) IS
        v_count NUMBER;
    BEGIN
        -- Check if user exists
        SELECT COUNT(*) INTO v_count FROM users WHERE id = p_user_id;
        IF v_count = 0 THEN
            RAISE_APPLICATION_ERROR(-20004, 'User not found');
        END IF;

        -- Update user
        UPDATE users
        SET username = NVL(p_username, username),
            email = NVL(p_email, email),
            password = NVL(p_password, password),
            updated_at = SYSTIMESTAMP
        WHERE id = p_user_id;

        COMMIT;
    EXCEPTION
        WHEN DUP_VAL_ON_INDEX THEN
            IF p_username IS NOT NULL THEN
                RAISE_APPLICATION_ERROR(-20001, 'Username already exists');
            ELSIF p_email IS NOT NULL THEN
                RAISE_APPLICATION_ERROR(-20002, 'Email already exists');
            ELSE
                RAISE;
            END IF;
        WHEN OTHERS THEN
            ROLLBACK;
            RAISE;
    END update_user;

    FUNCTION delete_user(p_user_id IN users.id%TYPE) RETURN NUMBER IS
        v_count NUMBER;
        v_deleted NUMBER;
    BEGIN
        -- Check if user exists
        SELECT COUNT(*) INTO v_count FROM users WHERE id = p_user_id;
        IF v_count = 0 THEN
            RAISE_APPLICATION_ERROR(-20004, 'User not found');
        END IF;

        -- Delete user
        DELETE FROM users WHERE id = p_user_id;
        v_deleted := SQL%ROWCOUNT;

        COMMIT;
        RETURN v_deleted;
    EXCEPTION
        WHEN OTHERS THEN
            ROLLBACK;
            RAISE;
    END delete_user;

    FUNCTION username_exists(p_username IN VARCHAR2) RETURN BOOLEAN IS
        v_count NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_count FROM users WHERE username = p_username;
        RETURN v_count > 0;
    END username_exists;

    FUNCTION email_exists(p_email IN VARCHAR2) RETURN BOOLEAN IS
        v_count NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_count FROM users WHERE email = p_email;
        RETURN v_count > 0;
    END email_exists;

    PROCEDURE log_login_error(
        p_username IN VARCHAR2,
        p_success IN NUMBER,
        p_error_code IN NUMBER,
        p_error_msg IN VARCHAR2,
        p_procedure_name IN VARCHAR2 DEFAULT 'log_login_attempt',
        p_error_level IN VARCHAR2 DEFAULT 'ERROR'
    ) IS
        PRAGMA AUTONOMOUS_TRANSACTION;
        v_context VARCHAR2(4000);
    BEGIN
        v_context := 'Logging login attempt for user: ' || p_username || ', success: ' || TO_CHAR(p_success);
        -- Use direct static INSERT instead of EXECUTE IMMEDIATE for better performance, readability, and safety
        -- id auto-generated via GENERATED ALWAYS AS IDENTITY
        INSERT INTO audit_error_log (error_code, error_message, context, procedure_name)
        VALUES (p_error_code, p_error_msg, v_context, p_procedure_name);
        COMMIT;
    END log_login_error;

    PROCEDURE log_login_attempt(
        p_username IN VARCHAR2,
        p_success IN NUMBER,
        p_ip_address IN VARCHAR2 DEFAULT NULL,
        p_user_agent IN VARCHAR2 DEFAULT NULL,
        p_failure_reason IN VARCHAR2 DEFAULT NULL
    ) IS
    BEGIN
        -- id auto-generated via GENERATED ALWAYS AS IDENTITY
        INSERT INTO audit_login_attempts (username, success, ip_address, user_agent, failure_reason)
        VALUES (p_username, p_success, p_ip_address, p_user_agent, p_failure_reason);
        COMMIT;
    EXCEPTION
        -- Targeted exception handling: swallow only benign errors (e.g., missing audit table ORA-00942)
        -- For other exceptions, log details to audit_error_log table and re-raise
        WHEN OTHERS THEN
            IF SQLCODE = -942 THEN  -- ORA-00942: table or view does not exist
                NULL;  -- Swallow missing audit table error to avoid failing login process
            ELSE
                -- Log unexpected errors and re-raise
                BEGIN
                    log_login_error(p_username, p_success, SQLCODE, SUBSTR(SQLERRM, 1, 4000));
                EXCEPTION
                    WHEN OTHERS THEN
                        NULL;  -- Guard logging itself to prevent propagation
                END;
                RAISE;  -- Re-raise the original exception
            END IF;
    END log_login_attempt;

    PROCEDURE log_session_error(
        p_user_id IN users.id%TYPE,
        p_token_hash IN VARCHAR2,
        p_error_code IN NUMBER,
        p_error_msg IN VARCHAR2,
        p_procedure_name IN VARCHAR2 DEFAULT 'log_session_start',
        p_error_level IN VARCHAR2 DEFAULT 'ERROR'
    ) IS
        PRAGMA AUTONOMOUS_TRANSACTION;
        v_context VARCHAR2(4000);
    BEGIN
        v_context := 'Logging session start for user: ' || NVL(TO_CHAR(p_user_id), 'N/A') || ', token_hash: ' || p_token_hash;
        -- Use direct static INSERT instead of EXECUTE IMMEDIATE for better performance, readability, and safety
        -- id auto-generated via GENERATED ALWAYS AS IDENTITY
        INSERT INTO audit_error_log (error_code, error_message, context, procedure_name, user_id)
        VALUES (p_error_code, p_error_msg, v_context, p_procedure_name, p_user_id);
        COMMIT;
    END log_session_error;

    PROCEDURE log_session_start(
        p_user_id IN users.id%TYPE,
        p_token_hash IN VARCHAR2,
        p_ip_address IN VARCHAR2 DEFAULT NULL,
        p_user_agent IN VARCHAR2 DEFAULT NULL
    ) IS
    BEGIN
        -- id auto-generated via GENERATED ALWAYS AS IDENTITY
        INSERT INTO audit_sessions (user_id, token_hash, ip_address, user_agent)
        VALUES (p_user_id, p_token_hash, p_ip_address, p_user_agent);
        COMMIT;
    EXCEPTION
        WHEN OTHERS THEN
            -- Log unexpected errors to audit_error_log while preserving session flow
            BEGIN
                log_session_error(p_user_id, p_token_hash, SQLCODE, SUBSTR(SQLERRM, 1, 4000));
            EXCEPTION
                WHEN OTHERS THEN
                    NULL;  -- Guard logging itself to prevent propagation
            END;
    END log_session_start;

    PROCEDURE log_mfa_event(
        p_user_id IN users.id%TYPE,
        p_admin_id IN users.id%TYPE DEFAULT NULL,
        p_event_type IN VARCHAR2,
        p_mfa_method IN VARCHAR2 DEFAULT NULL,
        p_status IN VARCHAR2,
        p_details IN VARCHAR2 DEFAULT NULL,
        p_ip_address IN VARCHAR2 DEFAULT NULL,
        p_user_agent IN VARCHAR2 DEFAULT NULL
    ) IS
    BEGIN
        -- Log MFA events to audit_error_log with context containing all event details
        INSERT INTO audit_error_log (error_code, error_message, context, procedure_name, user_id)
        VALUES ('MFA_EVENT', p_event_type,
            'user_id=' || NVL(TO_CHAR(p_user_id), 'N/A') || ', admin_id=' || NVL(TO_CHAR(p_admin_id), 'N/A') ||
            ', mfa_method=' || NVL(p_mfa_method, 'N/A') || ', status=' || p_status ||
            ', details=' || NVL(p_details, 'N/A') || ', ip_address=' || NVL(p_ip_address, 'N/A') ||
            ', user_agent=' || NVL(p_user_agent, 'N/A'),
            'log_mfa_event', p_user_id);
        COMMIT;
    EXCEPTION
        WHEN OTHERS THEN
            IF SQLCODE = -942 THEN  -- ORA-00942: table or view does not exist
                NULL;  -- Swallow missing audit table error
            ELSE
                BEGIN
                    log_session_error(p_user_id, 'N/A', SQLCODE, SUBSTR(SQLERRM, 1, 4000), 'log_mfa_event');
                EXCEPTION
                    WHEN OTHERS THEN
                        NULL;  -- Guard logging itself
                END;
                RAISE;
            END IF;
    END log_mfa_event;
END user_pkg;
/