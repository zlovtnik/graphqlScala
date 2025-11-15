-- Simplified definition that avoids partitioning/compression for XE.
CREATE TABLE audit_sessions (
    id NUMBER PRIMARY KEY,
    user_id VARCHAR2(36) NOT NULL,
    token_hash VARCHAR2(255) NOT NULL,
    ip_address VARCHAR2(45),
    user_agent VARCHAR2(500),
    created_at TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT fk_audit_sessions_user_id FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE INDEX idx_audit_sessions_user_id ON audit_sessions(user_id);
CREATE INDEX idx_audit_sessions_token_hash ON audit_sessions(token_hash);

CREATE INDEX idx_audit_sessions_created_at ON audit_sessions(created_at);
CREATE INDEX idx_audit_sessions_user_created ON audit_sessions(user_id, created_at);
CREATE INDEX idx_audit_sessions_ip_created ON audit_sessions(ip_address, created_at);