-- Create audit_login_attempts table
CREATE TABLE audit_login_attempts (
    id NUMBER PRIMARY KEY DEFAULT audit_seq.NEXTVAL,
    username VARCHAR2(255) NOT NULL,
    success NUMBER(1) NOT NULL, -- 0 or 1
    ip_address VARCHAR2(45),
    user_agent VARCHAR2(500),
    failure_reason VARCHAR2(500),
    created_at TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT chk_login_success CHECK (success IN (0, 1))
);

-- Indexes for audit_login_attempts
CREATE INDEX idx_audit_login_attempts_username ON audit_login_attempts(username);
CREATE INDEX idx_audit_login_attempts_success ON audit_login_attempts(success);
CREATE INDEX idx_audit_login_attempts_created_at ON audit_login_attempts(created_at);

-- Additional indexes for security monitoring
CREATE INDEX idx_audit_login_attempts_ip ON audit_login_attempts(ip_address);
CREATE INDEX idx_audit_login_attempts_username_created ON audit_login_attempts(username, created_at);
CREATE INDEX idx_audit_login_attempts_success_created ON audit_login_attempts(success, created_at);
CREATE INDEX idx_audit_login_attempts_ip_created ON audit_login_attempts(ip_address, created_at);