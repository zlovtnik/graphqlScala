-- Create audit_error_log table for logging exceptions in audit procedures
CREATE TABLE audit_error_log (
    id NUMBER DEFAULT audit_seq.NEXTVAL PRIMARY KEY,
    timestamp TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL,
    error_code VARCHAR2(10) NOT NULL,
    error_message VARCHAR2(4000),
    context VARCHAR2(4000),
    procedure_name VARCHAR2(100) NOT NULL
);