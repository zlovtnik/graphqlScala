-- Create audit sequence if it doesn't exist
DECLARE
    seq_exists NUMBER;
BEGIN
    SELECT CASE WHEN EXISTS (SELECT 1 FROM user_sequences WHERE sequence_name = 'AUDIT_SEQ')
           THEN 1 ELSE 0 END INTO seq_exists FROM dual;
    IF seq_exists = 0 THEN
        EXECUTE IMMEDIATE 'CREATE SEQUENCE audit_seq START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE';
    END IF;
END;
/