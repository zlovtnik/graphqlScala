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

    -- Function to delete user and return rows deleted
    FUNCTION delete_user(p_user_id IN VARCHAR2) RETURN NUMBER;

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