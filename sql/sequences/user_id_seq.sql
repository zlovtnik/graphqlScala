-- Sequence for user identifiers
BEGIN
    EXECUTE IMMEDIATE 'CREATE SEQUENCE user_id_seq
        START WITH 1
        INCREMENT BY 1
        MINVALUE 1
        NOCYCLE
        CACHE 50';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE = -955 THEN
            NULL; -- Sequence already exists
        ELSE
            RAISE;
        END IF;
END;
/
