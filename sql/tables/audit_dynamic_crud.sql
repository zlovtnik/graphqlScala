-- The XE build that powers local development does not expose partitioning or
-- advanced compression. Keep the table definition simple so bootstrap can run
-- everywhere while higher editions can layer features via regular migrations.
-- Oracle 12c+ supports GENERATED AS IDENTITY for automatic PK generation.
CREATE TABLE audit_dynamic_crud (
    id NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    table_name VARCHAR2(128) NOT NULL,
    operation VARCHAR2(10) NOT NULL,
    actor VARCHAR2(128),
    trace_id VARCHAR2(128),
    client_ip VARCHAR2(45),
    metadata CLOB,
    affected_rows NUMBER,
    status VARCHAR2(20) NOT NULL,
    message VARCHAR2(4000),
    error_code VARCHAR2(50),
    created_at TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL
);