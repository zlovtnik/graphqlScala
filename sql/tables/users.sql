-- Ensure user_id_seq exists so DEFAULT expression can reference it
DECLARE
    v_seq_exists NUMBER := 0;
BEGIN
    SELECT COUNT(*) INTO v_seq_exists
    FROM user_sequences
    WHERE sequence_name = 'USER_ID_SEQ';

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
        raise_application_error(-20000, 'Table already exists'); -- Table already exists
END;
/