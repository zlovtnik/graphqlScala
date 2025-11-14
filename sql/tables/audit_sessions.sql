-- Create audit_sessions table
CREATE TABLE audit_sessions (
    id NUMBER PRIMARY KEY DEFAULT audit_seq.NEXTVAL,
    user_id VARCHAR2(36) NOT NULL,
    token_hash VARCHAR2(255) NOT NULL,
    ip_address VARCHAR2(45),
    user_agent VARCHAR2(500),
    created_at TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT fk_audit_sessions_user_id FOREIGN KEY (user_id) REFERENCES users(id)
);

-- Indexes for audit_sessions
CREATE INDEX idx_audit_sessions_user_id ON audit_sessions(user_id);
CREATE INDEX idx_audit_sessions_token_hash ON audit_sessions(token_hash);

-- Additional indexes for session management
CREATE INDEX idx_audit_sessions_created_at ON audit_sessions(created_at);
CREATE INDEX idx_audit_sessions_user_created ON audit_sessions(user_id, created_at);
CREATE INDEX idx_audit_sessions_ip_created ON audit_sessions(ip_address, created_at);