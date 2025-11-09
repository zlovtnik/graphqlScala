-- Oracle Schema for GraphQL Scala App
-- All business logic implemented in procedures and functions, no triggers used.
--
-- IMPORTANT: Before executing this script, run grant_privileges.sql as SYS/DBA to grant necessary privileges to the application user.

-- Make script idempotent for development

-- Add columns to users table if not exist
DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_count FROM user_tab_columns WHERE table_name = 'USERS' AND column_name = 'CREATED_AT';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE users ADD (created_at TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL)';
    END IF;
    
    SELECT COUNT(*) INTO v_count FROM user_tab_columns WHERE table_name = 'USERS' AND column_name = 'UPDATED_AT';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE users ADD (updated_at TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL)';
    END IF;
END;
/

-- Create users table (skip if exists)
BEGIN
    EXECUTE IMMEDIATE 'CREATE TABLE users (
        id VARCHAR2(36) PRIMARY KEY,
        username VARCHAR2(255) NOT NULL UNIQUE,
        password VARCHAR2(255) NOT NULL,
        email VARCHAR2(255) NOT NULL UNIQUE,
        created_at TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL,
        updated_at TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL
    )';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE != -955 THEN
            RAISE;
        END IF;
END;
/

-- Create indexes (skip if exists)
BEGIN
    EXECUTE IMMEDIATE 'CREATE INDEX idx_users_username ON users(username)';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE != -1408 THEN
            RAISE;
        END IF;
END;
/

BEGIN
    EXECUTE IMMEDIATE 'CREATE INDEX idx_users_email ON users(email)';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE != -1408 THEN
            RAISE;
        END IF;
END;
/

-- Create audit tables (skip if exists)
BEGIN
    EXECUTE IMMEDIATE 'CREATE TABLE audit_login_attempts (
        id NUMBER PRIMARY KEY,
        username VARCHAR2(255) NOT NULL,
        attempt_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
        success NUMBER(1) NOT NULL, -- 1 for success, 0 for failure
        ip_address VARCHAR2(45), -- IPv4 or IPv6
        user_agent VARCHAR2(500),
        failure_reason VARCHAR2(255)
    )';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE != -955 THEN
            RAISE;
        END IF;
END;
/

BEGIN
    EXECUTE IMMEDIATE 'CREATE TABLE audit_sessions (
        id NUMBER PRIMARY KEY,
        user_id VARCHAR2(36) NOT NULL,
        token_hash VARCHAR2(64), -- SHA-256 hash of token
        start_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
        ip_address VARCHAR2(45),
        user_agent VARCHAR2(500),
        FOREIGN KEY (user_id) REFERENCES users(id)
    )';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE != -955 THEN
            RAISE;
        END IF;
END;
/

-- Create sequence (skip if exists)
BEGIN
    EXECUTE IMMEDIATE 'CREATE SEQUENCE audit_seq START WITH 1 INCREMENT BY 1';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE != -955 THEN
            RAISE;
        END IF;
END;
/

-- Create package for user operations
CREATE OR REPLACE PACKAGE user_pkg AS
    -- Procedure to create a new user
    PROCEDURE create_user(
        p_username IN VARCHAR2,
        p_password IN VARCHAR2,
        p_email IN VARCHAR2,
        p_user_id OUT VARCHAR2
    );

    -- Function to get user by ID
    FUNCTION get_user_by_id(p_user_id IN VARCHAR2) RETURN SYS_REFCURSOR;

    -- Function to get user by username
    FUNCTION get_user_by_username(p_username IN VARCHAR2) RETURN SYS_REFCURSOR;

    -- Function to get user by email
    FUNCTION get_user_by_email(p_email IN VARCHAR2) RETURN SYS_REFCURSOR;

    -- Procedure to update user
    PROCEDURE update_user(
        p_user_id IN VARCHAR2,
        p_username IN VARCHAR2 DEFAULT NULL,
        p_email IN VARCHAR2 DEFAULT NULL,
        p_password IN VARCHAR2 DEFAULT NULL
    );

    -- Procedure to delete user
    PROCEDURE delete_user(p_user_id IN VARCHAR2);

    -- Function to check if username exists
    FUNCTION username_exists(p_username IN VARCHAR2) RETURN BOOLEAN;

    -- Function to check if email exists
    FUNCTION email_exists(p_email IN VARCHAR2) RETURN BOOLEAN;

    -- Procedure to log login attempt
    PROCEDURE log_login_attempt(
        p_username IN VARCHAR2,
        p_success IN NUMBER,
        p_ip_address IN VARCHAR2 DEFAULT NULL,
        p_user_agent IN VARCHAR2 DEFAULT NULL,
        p_failure_reason IN VARCHAR2 DEFAULT NULL
    );

    -- Procedure to log session start
    PROCEDURE log_session_start(
        p_user_id IN VARCHAR2,
        p_token_hash IN VARCHAR2,
        p_ip_address IN VARCHAR2 DEFAULT NULL,
        p_user_agent IN VARCHAR2 DEFAULT NULL
    );
END user_pkg;
/

CREATE OR REPLACE PACKAGE BODY user_pkg AS
    -- Helper function to generate UUID
    FUNCTION generate_uuid RETURN VARCHAR2 IS
        v_uuid RAW(16);
        v_hex VARCHAR2(32);
    BEGIN
        v_uuid := SYS_GUID();
        v_hex := RAWTOHEX(v_uuid);
        RETURN LOWER(SUBSTR(v_hex, 1, 8) || '-' || SUBSTR(v_hex, 9, 4) || '-' || SUBSTR(v_hex, 13, 4) || '-' || SUBSTR(v_hex, 17, 4) || '-' || SUBSTR(v_hex, 21, 12));
    END generate_uuid;

    PROCEDURE create_user(
        p_username IN VARCHAR2,
        p_password IN VARCHAR2,
        p_email IN VARCHAR2,
        p_user_id OUT VARCHAR2
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

        -- Generate UUID
        p_user_id := generate_uuid();

        -- Insert user (password is already hashed by application)
        INSERT INTO users (id, username, password, email, created_at, updated_at)
        VALUES (p_user_id, p_username, p_password, p_email, SYSTIMESTAMP, SYSTIMESTAMP);

        COMMIT;
    EXCEPTION
        WHEN OTHERS THEN
            ROLLBACK;
            RAISE;
    END create_user;

    FUNCTION get_user_by_id(p_user_id IN VARCHAR2) RETURN SYS_REFCURSOR IS
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
        p_user_id IN VARCHAR2,
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

    PROCEDURE delete_user(p_user_id IN VARCHAR2) IS
        v_count NUMBER;
    BEGIN
        -- Check if user exists
        SELECT COUNT(*) INTO v_count FROM users WHERE id = p_user_id;
        IF v_count = 0 THEN
            RAISE_APPLICATION_ERROR(-20004, 'User not found');
        END IF;

        -- Delete user
        DELETE FROM users WHERE id = p_user_id;

        COMMIT;
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

    PROCEDURE log_login_attempt(
        p_username IN VARCHAR2,
        p_success IN NUMBER,
        p_ip_address IN VARCHAR2 DEFAULT NULL,
        p_user_agent IN VARCHAR2 DEFAULT NULL,
        p_failure_reason IN VARCHAR2 DEFAULT NULL
    ) IS
    BEGIN
        INSERT INTO audit_login_attempts (id, username, success, ip_address, user_agent, failure_reason)
        VALUES (audit_seq.NEXTVAL, p_username, p_success, p_ip_address, p_user_agent, p_failure_reason);
        COMMIT;
    EXCEPTION
        WHEN OTHERS THEN
            -- Log error but don't fail the login process
            NULL;
    END log_login_attempt;

    PROCEDURE log_session_start(
        p_user_id IN VARCHAR2,
        p_token_hash IN VARCHAR2,
        p_ip_address IN VARCHAR2 DEFAULT NULL,
        p_user_agent IN VARCHAR2 DEFAULT NULL
    ) IS
    BEGIN
        INSERT INTO audit_sessions (id, user_id, token_hash, ip_address, user_agent)
        VALUES (audit_seq.NEXTVAL, p_user_id, p_token_hash, p_ip_address, p_user_agent);
        COMMIT;
    EXCEPTION
        WHEN OTHERS THEN
            -- Log error but don't fail the session
            NULL;
    END log_session_start;
END user_pkg;
/