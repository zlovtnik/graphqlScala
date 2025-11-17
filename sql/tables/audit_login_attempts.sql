-- Simplified definition that works on Oracle XE (no partitions/compression).
CREATE TABLE audit_login_attempts (
    id NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    username VARCHAR2(255) NOT NULL,
    success NUMBER(1) NOT NULL, -- 0 or 1
    ip_address VARCHAR2(45),
    user_agent VARCHAR2(2000),
    failure_reason VARCHAR2(500),
    created_at TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT chk_login_success CHECK (success IN (0, 1))
);

-- Indexes for audit_login_attempts
-- Removed redundant single-column indexes which duplicate leading columns of composite indexes
-- Rationale: composite indexes (username, created_at), (success, created_at), (ip_address, created_at)
-- provide the necessary access patterns; single-column indexes on those leading columns are redundant
-- and add extra space/maintenance cost. Retain ip single-column index if single-column lookups on IP
-- are required; otherwise rely on (ip_address, created_at).

-- (Single-column indexes dropped to reduce redundancy)
-- CREATE INDEX idx_audit_login_attempts_username ON audit_login_attempts(username);
-- CREATE INDEX idx_audit_login_attempts_success ON audit_login_attempts(success);
-- CREATE INDEX idx_audit_login_attempts_created_at ON audit_login_attempts(created_at);

-- Additional indexes for security monitoring
CREATE INDEX idx_audit_login_attempts_ip ON audit_login_attempts(ip_address);
CREATE INDEX idx_audit_login_attempts_username_created ON audit_login_attempts(username, created_at);
CREATE INDEX idx_audit_login_attempts_success_created ON audit_login_attempts(success, created_at);
CREATE INDEX idx_audit_login_attempts_ip_created ON audit_login_attempts(ip_address, created_at);

-- Optional composite index to support failed-attempt count queries efficiently by IP+username
-- Useful for queries that filter by ip_address AND username AND created_at window (e.g., windowed failed attempts)
CREATE INDEX idx_audit_login_attempts_ip_username_created ON audit_login_attempts(ip_address, username, created_at);