CREATE TABLE audit_error_log (
	id NUMBER PRIMARY KEY,
	error_code VARCHAR2(50),
	error_message VARCHAR2(4000),
	context VARCHAR2(4000),
	procedure_name VARCHAR2(128),
	stack_trace CLOB,
	created_at TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL
)
ROW STORE COMPRESS ADVANCED
PARTITION BY RANGE (created_at)
(
	PARTITION P_1970_01 VALUES LESS THAN (TO_DATE('1970-02-01','YYYY-MM-DD')),
	PARTITION P_9999_12 VALUES LESS THAN (TO_DATE('9999-12-31','YYYY-MM-DD'))
);

CREATE INDEX idx_audit_error_log_created_at ON audit_error_log(created_at) LOCAL COMPRESS ADVANCED LOW;
CREATE INDEX idx_audit_error_log_error_code ON audit_error_log(error_code) LOCAL COMPRESS ADVANCED LOW;
CREATE INDEX idx_audit_error_log_procedure_name ON audit_error_log(procedure_name) LOCAL COMPRESS ADVANCED LOW;
CREATE INDEX idx_audit_error_log_error_created ON audit_error_log(error_code, created_at) LOCAL COMPRESS ADVANCED LOW;