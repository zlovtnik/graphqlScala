-- Simplified definition compatible with Oracle XE.
CREATE TABLE audit_error_log (
	id NUMBER PRIMARY KEY,
	error_code VARCHAR2(50),
	error_message VARCHAR2(4000),
	context VARCHAR2(4000),
	procedure_name VARCHAR2(128),
	stack_trace CLOB,
	created_at TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL
);

CREATE INDEX idx_audit_error_log_created_at ON audit_error_log(created_at);
CREATE INDEX idx_audit_error_log_error_code ON audit_error_log(error_code);
CREATE INDEX idx_audit_error_log_procedure_name ON audit_error_log(procedure_name);
CREATE INDEX idx_audit_error_log_error_created ON audit_error_log(error_code, created_at);