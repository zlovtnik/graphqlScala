-- Migration: Add user profile fields for settings management (V500)
-- Extends users table with avatar storage and account status tracking

BEGIN
    EXECUTE IMMEDIATE 'ALTER TABLE users ADD avatar_key VARCHAR2(255)';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE = -1430 THEN
            NULL; -- Column already exists
        ELSE
            RAISE;
        END IF;
END;
/

BEGIN
    EXECUTE IMMEDIATE 'ALTER TABLE users ADD account_status VARCHAR2(50) DEFAULT ''ACTIVE''';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE = -1430 THEN
            NULL; -- Column already exists
        ELSE
            RAISE;
        END IF;
END;
/

BEGIN
    EXECUTE IMMEDIATE 'ALTER TABLE users ADD account_deactivated_at NUMBER(19)';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE = -1430 THEN
            NULL; -- Column already exists
        ELSE
            RAISE;
        END IF;
END;
/

-- Create user_preferences table for storing user preferences
BEGIN
    EXECUTE IMMEDIATE 'CREATE TABLE user_preferences (
        id NUMBER(19) DEFAULT user_id_seq.NEXTVAL PRIMARY KEY,
        user_id NUMBER(19) NOT NULL UNIQUE,
        theme VARCHAR2(50) DEFAULT ''light'' NOT NULL,
        language VARCHAR2(10) DEFAULT ''en'' NOT NULL,
        notification_emails NUMBER(1) DEFAULT 1 NOT NULL,
        notification_push NUMBER(1) DEFAULT 1 NOT NULL,
        notification_login_alerts NUMBER(1) DEFAULT 1 NOT NULL,
        notification_security_updates NUMBER(1) DEFAULT 1 NOT NULL,
        created_at NUMBER(19) NOT NULL,
        updated_at NUMBER(19) NOT NULL,
        CONSTRAINT fk_user_prefs_user_id FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
    )';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE = -955 THEN
            NULL; -- Table already exists
        ELSE
            RAISE;
        END IF;
END;
/

-- Create index for faster preference lookups
BEGIN
    EXECUTE IMMEDIATE 'CREATE INDEX idx_user_prefs_user_id ON user_preferences(user_id)';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE = -955 THEN
            NULL; -- Index already exists
        ELSE
            RAISE;
        END IF;
END;
/

-- Create api_keys table for API key lifecycle management
BEGIN
    EXECUTE IMMEDIATE 'CREATE TABLE api_keys (
        id NUMBER(19) DEFAULT user_id_seq.NEXTVAL PRIMARY KEY,
        user_id NUMBER(19) NOT NULL,
        key_name VARCHAR2(255) NOT NULL,
        key_hash VARCHAR2(255) NOT NULL,
        key_preview VARCHAR2(255),
        last_used_at NUMBER(19),
        revoked_at NUMBER(19),
        expires_at NUMBER(19),
        created_at NUMBER(19) NOT NULL,
        updated_at NUMBER(19) NOT NULL,
        CONSTRAINT fk_api_key_user_id FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
        CONSTRAINT unique_api_key_hash UNIQUE (key_hash)
    )';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE = -955 THEN
            NULL; -- Table already exists
        ELSE
            RAISE;
        END IF;
END;
/

-- Create indexes for API key queries
BEGIN
    EXECUTE IMMEDIATE 'CREATE INDEX idx_api_key_user_id ON api_keys(user_id)';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE = -955 THEN
            NULL; -- Index already exists
        ELSE
            RAISE;
        END IF;
END;
/

BEGIN
    EXECUTE IMMEDIATE 'CREATE INDEX idx_api_key_active ON api_keys(user_id, revoked_at, expires_at)';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE = -955 THEN
            NULL; -- Index already exists
        ELSE
            RAISE;
        END IF;
END;
/

-- Create account_deactivation_audit table for tracking account deactivations
BEGIN
    EXECUTE IMMEDIATE 'CREATE TABLE account_deactivation_audit (
        id NUMBER(19) DEFAULT user_id_seq.NEXTVAL PRIMARY KEY,
        user_id NUMBER(19) NOT NULL,
        timestamp NUMBER(19) NOT NULL,
        reason_code VARCHAR2(100),
        justification CLOB,
        CONSTRAINT fk_audit_user_id FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
    )';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE = -955 THEN
            NULL; -- Table already exists
        ELSE
            RAISE;
        END IF;
END;
/

-- Create indexes for audit queries
BEGIN
    EXECUTE IMMEDIATE 'CREATE INDEX idx_deactivation_audit_user_id ON account_deactivation_audit(user_id)';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE = -955 THEN
            NULL; -- Index already exists
        ELSE
            RAISE;
        END IF;
END;
/

BEGIN
    EXECUTE IMMEDIATE 'CREATE INDEX idx_deactivation_audit_timestamp ON account_deactivation_audit(timestamp)';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE = -955 THEN
            NULL; -- Index already exists
        ELSE
            RAISE;
        END IF;
END;
/

COMMIT;
