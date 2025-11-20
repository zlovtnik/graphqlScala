-- role_pkg_spec.sql
-- Package specification for role management procedures and functions
-- All procedures propagate exceptions to the caller; no internal transaction commits
-- Caller (Java @Transactional layer) manages all transaction boundaries

CREATE OR REPLACE PACKAGE role_pkg AS
    
    -- Custom exception declarations
    e_invalid_role EXCEPTION;
    PRAGMA EXCEPTION_INIT(e_invalid_role, -20001);
    
    e_role_already_granted EXCEPTION;
    PRAGMA EXCEPTION_INIT(e_role_already_granted, -20002);
    
    e_role_not_found EXCEPTION;
    PRAGMA EXCEPTION_INIT(e_role_not_found, -20003);
    
    e_user_not_found EXCEPTION;
    PRAGMA EXCEPTION_INIT(e_user_not_found, -20004);
    
    /**
     * Grant role to user with optional expiration
     * 
     * Raises:
     *   - e_user_not_found (ORA-20004): User does not exist
     *   - e_invalid_role (ORA-20001): Role does not exist
     *   - e_role_already_granted (ORA-20002): User already has this role
     *   - DUP_VAL_ON_INDEX: Role assignment already exists (database constraint)
     * 
     * Transaction: Caller-managed. No internal COMMIT. Exceptions propagate to caller.
     */
    PROCEDURE grant_role(
        p_user_id IN users.id%TYPE,
        p_role_name IN VARCHAR2,
        p_granted_by IN users.id%TYPE,
        p_expires_at IN TIMESTAMP DEFAULT NULL,
        p_ip_address IN VARCHAR2 DEFAULT NULL
    );
    
    /**
     * Revoke role from user
     * 
     * Raises:
     *   - e_user_not_found (ORA-20004): User does not exist
     *   - e_invalid_role (ORA-20001): Role does not exist
     *   - NO_DATA_FOUND: User does not have this role
     * 
     * Transaction: Caller-managed. No internal COMMIT. Exceptions propagate to caller.
     */
    PROCEDURE revoke_role(
        p_user_id IN users.id%TYPE,
        p_role_name IN VARCHAR2,
        p_revoked_by IN users.id%TYPE,
        p_reason IN VARCHAR2 DEFAULT NULL,
        p_ip_address IN VARCHAR2 DEFAULT NULL
    );
    
    /**
     * Get all roles for a user
     * 
     * Raises:
     *   - e_user_not_found (ORA-20004): User does not exist
     * 
     * Returns: SYS_REFCURSOR with active role records (excludes expired roles)
     * Transaction: Read-only. Caller-managed cursor lifecycle.
     */
    FUNCTION get_user_roles(p_user_id IN users.id%TYPE) RETURN SYS_REFCURSOR;
    
    /**
     * Check if user has specific role
     * 
     * Raises:
     *   - e_user_not_found (ORA-20004): User does not exist
     *   - e_invalid_role (ORA-20001): Role does not exist
     * 
     * Returns: BOOLEAN TRUE if user has active role, FALSE otherwise
     * Transaction: Read-only. No transaction impact.
     */
    FUNCTION has_role(
        p_user_id IN users.id%TYPE,
        p_role_name IN VARCHAR2
    ) RETURN BOOLEAN;
    
    /**
     * Expire roles with past expiration dates (scheduled task)
     * 
     * Raises:
     *   - Other exceptions during role deletion or audit creation (propagates to caller)
     * 
     * Transaction: Caller-managed. No internal COMMIT. Deletes all expired user_roles
     *              and creates corresponding EXPIRE audit entries.
     */
    PROCEDURE expire_roles;
    
    /**
     * Get role audit entries
     * 
     * Raises:
     *   - Other exceptions during cursor fetch
     * 
     * Parameters:
     *   - p_user_id: Optional filter by specific user (NULL returns all)
     *   - p_limit: Max rows to fetch (default 100)
     *   - p_offset: Pagination offset (default 0)
     * 
     * Returns: SYS_REFCURSOR with audit_role_changes records ordered by created_at DESC
     * Transaction: Read-only. Caller-managed cursor lifecycle.
     */
    FUNCTION get_role_audit_log(
        p_user_id IN users.id%TYPE DEFAULT NULL,
        p_limit IN NUMBER DEFAULT 100,
        p_offset IN NUMBER DEFAULT 0
    ) RETURN SYS_REFCURSOR;
    
END role_pkg;
/
