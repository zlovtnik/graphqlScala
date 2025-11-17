-- Simplified definition that avoids partitioning/compression for XE.
-- Oracle 12c+ supports GENERATED AS IDENTITY for automatic PK generation.
-- user_agent increased to 2000 chars to accommodate modern user-agent strings.
CREATE TABLE audit_sessions (
    id NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id NUMBER(19) NOT NULL,
    token_hash VARCHAR2(255) NOT NULL,
    ip_address VARCHAR2(45),
    user_agent VARCHAR2(2000),
    created_at TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT fk_audit_sessions_user_id FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE INDEX idx_audit_sessions_user_id ON audit_sessions(user_id);
CREATE INDEX idx_audit_sessions_token_hash ON audit_sessions(token_hash);

CREATE INDEX idx_audit_sessions_created_at ON audit_sessions(created_at);
CREATE INDEX idx_audit_sessions_user_created ON audit_sessions(user_id, created_at);
CREATE INDEX idx_audit_sessions_ip_created ON audit_sessions(ip_address, created_at);