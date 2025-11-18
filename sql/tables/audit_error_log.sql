-- Simplified definition compatible with Oracle XE.
CREATE TABLE audit_error_log (
	id NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
	error_code VARCHAR2(50),
	error_message VARCHAR2(4000),
	context VARCHAR2(4000),
	procedure_name VARCHAR2(128),
	stack_trace CLOB,
	user_id NUMBER(19),
	session_id NUMBER(19),
	error_level VARCHAR2(20),
	created_at TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
	CONSTRAINT chk_audit_error_log_level CHECK (error_level IN ('INFO', 'WARN', 'ERROR', 'CRITICAL')),
	CONSTRAINT fk_audit_error_log_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL,
	CONSTRAINT fk_audit_error_log_session FOREIGN KEY (session_id) REFERENCES audit_sessions(id) ON DELETE SET NULL
);

CREATE INDEX idx_audit_error_log_created_at ON audit_error_log(created_at);
CREATE INDEX idx_audit_error_log_error_code ON audit_error_log(error_code);
CREATE INDEX idx_audit_error_log_procedure_name ON audit_error_log(procedure_name);
CREATE INDEX idx_audit_error_log_error_created ON audit_error_log(error_code, created_at);
CREATE INDEX idx_audit_error_log_user_id ON audit_error_log(user_id);
CREATE INDEX idx_audit_error_log_session_id ON audit_error_log(session_id);
CREATE INDEX idx_audit_error_log_user_session ON audit_error_log(user_id, session_id);
CREATE INDEX idx_audit_error_log_level_created ON audit_error_log(error_level, created_at);