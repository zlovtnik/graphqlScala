-- Ensure sequence exists before table creation
DECLARE
    v_seq_exists NUMBER := 0;
BEGIN
    SELECT COUNT(*) INTO v_seq_exists
    FROM user_objects
    WHERE object_type = 'SEQUENCE'
      AND object_name = 'USER_ID_SEQ';

    IF v_seq_exists = 0 THEN
        EXECUTE IMMEDIATE 'CREATE SEQUENCE user_id_seq
            START WITH 1
            INCREMENT BY 1
            MINVALUE 1
            NOCYCLE
            CACHE 50';
    END IF;
END;
/

-- Create users table (skip if exists)
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