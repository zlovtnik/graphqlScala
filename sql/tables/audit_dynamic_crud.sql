-- Partitioned + compressed definition for audit_dynamic_crud table
CREATE TABLE audit_dynamic_crud (
    id NUMBER PRIMARY KEY,
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
)
ROW STORE COMPRESS ADVANCED
PARTITION BY RANGE (created_at)
(
    PARTITION P_1970_01 VALUES LESS THAN (TO_DATE('1970-02-01','YYYY-MM-DD')),
    PARTITION P_9999_12 VALUES LESS THAN (TO_DATE('9999-12-31','YYYY-MM-DD'))
);