CREATE TABLE audit_login_attempts (
    id NUMBER PRIMARY KEY DEFAULT audit_seq.NEXTVAL,
    username VARCHAR2(255) NOT NULL,
    success NUMBER(1) NOT NULL, -- 0 or 1
    ip_address VARCHAR2(45),
    user_agent VARCHAR2(500),
    failure_reason VARCHAR2(500),
    created_at TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT chk_login_success CHECK (success IN (0, 1))
)
ROW STORE COMPRESS ADVANCED
PARTITION BY RANGE (created_at)
(
    PARTITION P_1970_01 VALUES LESS THAN (TO_DATE('1970-02-01','YYYY-MM-DD')),
    PARTITION P_9999_12 VALUES LESS THAN (TO_DATE('9999-12-31','YYYY-MM-DD'))
);

-- Indexes for audit_login_attempts
CREATE INDEX idx_audit_login_attempts_username ON audit_login_attempts(username) LOCAL COMPRESS ADVANCED LOW;
CREATE INDEX idx_audit_login_attempts_success ON audit_login_attempts(success) LOCAL COMPRESS ADVANCED LOW;
CREATE INDEX idx_audit_login_attempts_created_at ON audit_login_attempts(created_at) LOCAL COMPRESS ADVANCED LOW;

-- Additional indexes for security monitoring
CREATE INDEX idx_audit_login_attempts_ip ON audit_login_attempts(ip_address) LOCAL COMPRESS ADVANCED LOW;
CREATE INDEX idx_audit_login_attempts_username_created ON audit_login_attempts(username, created_at) LOCAL COMPRESS ADVANCED LOW;
CREATE INDEX idx_audit_login_attempts_success_created ON audit_login_attempts(success, created_at) LOCAL COMPRESS ADVANCED LOW;
CREATE INDEX idx_audit_login_attempts_ip_created ON audit_login_attempts(ip_address, created_at) LOCAL COMPRESS ADVANCED LOW;