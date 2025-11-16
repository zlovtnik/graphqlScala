-- Create audit sequence if it doesn't exist
DECLARE
    seq_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO seq_count FROM user_sequences WHERE sequence_name = 'AUDIT_SEQ';
    IF seq_count = 0 THEN
        EXECUTE IMMEDIATE 'CREATE SEQUENCE audit_seq START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE';
    END IF;
END;
/