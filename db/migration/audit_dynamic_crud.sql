-- The XE build that powers local development does not expose partitioning or
-- advanced compression. Keep the table definition simple so bootstrap can run
-- everywhere while higher editions can layer features via regular migrations.
-- Oracle 12c+ supports GENERATED AS IDENTITY for automatic PK generation.
CREATE TABLE audit_dynamic_crud (
    id NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    table_name VARCHAR2(128) NOT NULL,
    operation VARCHAR2(10) NOT NULL CONSTRAINT chk_audit_dynamic_crud_operation CHECK (operation IN ('INSERT', 'UPDATE', 'DELETE', 'SELECT')),
    actor VARCHAR2(128),
    trace_id VARCHAR2(128),
    client_ip VARCHAR2(45),
    metadata CLOB,
    affected_rows NUMBER,
    status VARCHAR2(20) NOT NULL CONSTRAINT chk_audit_dynamic_crud_status CHECK (status IN ('SUCCESS', 'FAILURE', 'PENDING')),
    message VARCHAR2(4000),
    error_code VARCHAR2(50),
    created_at TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL
);

-- Indexes for common audit query patterns
-- Index for queries by table and operation
CREATE INDEX idx_audit_crud_table_op ON audit_dynamic_crud(table_name, operation);

-- Index for queries by actor (user who performed action)
CREATE INDEX idx_audit_crud_actor ON audit_dynamic_crud(actor);

-- Index for time-based queries
CREATE INDEX idx_audit_crud_created ON audit_dynamic_crud(created_at);

-- Index for tracing requests across systems
CREATE INDEX idx_audit_crud_trace ON audit_dynamic_crud(trace_id);

-- Composite index for common filtered queries (table + time range)
CREATE INDEX idx_audit_crud_table_created ON audit_dynamic_crud(table_name, created_at);
