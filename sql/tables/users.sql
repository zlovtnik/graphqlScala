-- Create users table (skip if exists)
-- Note: Sequence user_id_seq is created by sql/sequences/user_id_seq.sql
-- This script assumes the sequence exists before this table is created
BEGIN
    EXECUTE IMMEDIATE 'CREATE TABLE users (
        id NUMBER(19) DEFAULT user_id_seq.NEXTVAL PRIMARY KEY,
        username VARCHAR2(255) NOT NULL UNIQUE,
        password VARCHAR2(255) NOT NULL,
        email VARCHAR2(255) NOT NULL UNIQUE,
        created_at TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL,
        updated_at TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL
    )';
EXCEPTION
    WHEN OTHERS THEN
        -- Only suppress the "object already exists" error (ORA-00955)
        -- All other errors (permissions, syntax, tablespace, etc.) are re-raised
        IF SQLCODE = -955 THEN
            NULL;  -- Table already exists, continue
        ELSE
            RAISE;  -- Re-raise any other error for visibility
        END IF;
END;
/