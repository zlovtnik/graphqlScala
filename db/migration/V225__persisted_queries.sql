-- Persisted Queries Registry for GraphQL Query Optimization
-- This table stores common GraphQL queries for APQ (Automatic Persisted Queries) support
-- Reduces query size by 90%+ on repeated queries; enables query complexity analysis
--
-- IMPORTANT PRE-MIGRATION VALIDATION:
-- Before running this migration in environments with existing data, verify:
--
-- 1. Data Conformance:
--    a) No NULL values in complexity_score, failure_rate, slow_call_rate, compression_ratio columns
--    b) All complexity_score values >= 0
--    c) All failure_rate values in range 0-100
--    d) All slow_call_rate values in range 0-100
--    d) All compression_ratio values in range 0-100
--
--    Run this query to identify non-conforming rows BEFORE applying migration:
--    SELECT * FROM audit_graphql_complexity WHERE complexity_score IS NULL OR complexity_score < 0;
--    SELECT * FROM audit_circuit_breaker_events WHERE failure_rate IS NULL OR failure_rate NOT BETWEEN 0 AND 100;
--    SELECT * FROM audit_circuit_breaker_events WHERE slow_call_rate IS NULL OR slow_call_rate NOT BETWEEN 0 AND 100;
--    SELECT * FROM audit_http_compression WHERE compression_ratio IS NULL OR compression_ratio NOT BETWEEN 0 AND 100;
--
--    Consider integrating these validation queries into CI/CD pipelines for automated pre-migration checks.
--
-- 2. User Permissions:
--    Verify that the database user executing this migration (typically app_user) exists and matches
--    the APP_USER convention in your environment. If using a different user name (e.g., ssfuser),
--    update the GRANT statements below accordingly before running this migration.
--
-- 3. Rollback Strategy:
--    If migration fails due to non-conforming data:
--    a) Run UPDATE statements to fix NULL values or out-of-range values
--    b) For example: UPDATE audit_graphql_complexity SET complexity_score = 0 WHERE complexity_score IS NULL;
--    c) Then rerun this migration script

-- Pre-migration validation and data migration to handle existing rows
-- If the persisted_queries table exists with data, perform the following steps:
-- 1. Check for NULL or invalid values in NOT NULL columns
-- 2. Migrate existing data to match new schema constraints
-- 3. Handle user_id conversions if needed

BEGIN
    -- Check if table exists
    DECLARE
        v_table_exists NUMBER := 0;
        v_null_complexity_rows NUMBER := 0;
        v_null_version_rows NUMBER := 0;
        v_null_status_rows NUMBER := 0;
    BEGIN
        SELECT COUNT(*) INTO v_table_exists FROM user_tables WHERE table_name = 'PERSISTED_QUERIES';
        
        IF v_table_exists > 0 THEN
            -- Pre-migration validation queries
            SELECT COUNT(*) INTO v_null_complexity_rows FROM persisted_queries WHERE complexity_score IS NULL;
            SELECT COUNT(*) INTO v_null_version_rows FROM persisted_queries WHERE version IS NULL;
            SELECT COUNT(*) INTO v_null_status_rows FROM persisted_queries WHERE status IS NULL;
            
            -- Fix NULL complexity_score (set to 0)
            IF v_null_complexity_rows > 0 THEN
                UPDATE persisted_queries SET complexity_score = 0 WHERE complexity_score IS NULL;
                COMMIT;
            END IF;
            
            -- Fix NULL version (set to default)
            IF v_null_version_rows > 0 THEN
                UPDATE persisted_queries SET version = 'v1' WHERE version IS NULL;
                COMMIT;
            END IF;
            
            -- Fix NULL status (set to ACTIVE)
            IF v_null_status_rows > 0 THEN
                UPDATE persisted_queries SET status = 'ACTIVE' WHERE status IS NULL;
                COMMIT;
            END IF;
        END IF;
    END;
END;
/

CREATE TABLE persisted_queries (
    id VARCHAR2(64) PRIMARY KEY,
    query_hash VARCHAR2(64) NOT NULL UNIQUE,
    query_text CLOB NOT NULL,
    version VARCHAR2(10) DEFAULT 'v1' NOT NULL,
    complexity_score NUMBER(10) NOT NULL CONSTRAINT chk_persisted_queries_complexity_gte_0 CHECK (complexity_score >= 0),
    created_at TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL,
    updated_at TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL,
    last_used_at TIMESTAMP,
    usage_count NUMBER(19) DEFAULT 0 NOT NULL,
    client_name VARCHAR2(255),
    status VARCHAR2(20) DEFAULT 'ACTIVE' NOT NULL,
    created_by VARCHAR2(255),
    updated_by VARCHAR2(255)
) PARTITION BY RANGE (created_at) (
    -- Note: Using a single default partition for simplicity. For production environments with high data volumes,
    -- consider defining monthly or daily partitions to enable aging and maintenance policies.
    PARTITION p_default VALUES LESS THAN (MAXVALUE)
);

-- Indexes for query lookups and usage tracking
CREATE INDEX idx_persisted_queries_hash ON persisted_queries(query_hash);
CREATE INDEX idx_persisted_queries_version ON persisted_queries(version, status);
CREATE INDEX idx_persisted_queries_client ON persisted_queries(client_name, created_at DESC);
CREATE INDEX idx_persisted_queries_usage ON persisted_queries(usage_count DESC, last_used_at DESC);

-- Query Complexity Analysis Audit Table
CREATE TABLE audit_graphql_complexity (
    id NUMBER(19) GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    query_hash VARCHAR2(64) NOT NULL,
    complexity_score NUMBER(10) NOT NULL CONSTRAINT chk_audit_complexity_score_gte_0 CHECK (complexity_score >= 0),
    max_allowed NUMBER(10) NOT NULL,
    status VARCHAR2(20) NOT NULL, -- ACCEPTED, REJECTED
    client_ip VARCHAR2(45),
    user_id NUMBER(19),
    attempted_at TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL,
    resolution_suggestion VARCHAR2(1000),
    query_preview VARCHAR2(500),
    CONSTRAINT fk_audit_complexity_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL
) PARTITION BY RANGE (attempted_at) (
    -- Note: Using a single default partition for simplicity. For production environments with high data volumes,
    -- consider defining monthly or daily partitions to enable aging and maintenance policies.
    PARTITION p_default VALUES LESS THAN (MAXVALUE)
);

CREATE INDEX idx_audit_complexity_hash ON audit_graphql_complexity(query_hash, attempted_at DESC);
CREATE INDEX idx_audit_complexity_status ON audit_graphql_complexity(status, attempted_at DESC);
CREATE INDEX idx_audit_complexity_ip ON audit_graphql_complexity(client_ip, attempted_at DESC);

-- GraphQL Execution Plan Cache Statistics Table
CREATE TABLE audit_graphql_execution_plans (
    id NUMBER(19) GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    query_hash VARCHAR2(64) NOT NULL,
    execution_plan CLOB,
    p50_time_ms NUMBER(10),
    p95_time_ms NUMBER(10),
    p99_time_ms NUMBER(10),
    resolver_breakdown CLOB, -- JSON format
    cache_hit CHAR(1) DEFAULT 'N' NOT NULL,
    sampled_at TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL,
    anomaly_detected CHAR(1) DEFAULT 'N' NOT NULL,
    -- IMPORTANT: do not cascade deletes from persisted_queries to audit tables
    -- Audit records should remain independent and preserved even if a persisted
    -- query is deleted or rotated. We intentionally do not enforce a cascading
    -- foreign key constraint here. Keep the query_hash column as a plain field
    -- for traceability and integrity of historical data.
    -- (The application can optionally check for an existing persisted query by hash.)
) PARTITION BY RANGE (sampled_at) (
    -- Note: Using a single default partition for simplicity. For production environments with high data volumes,
    -- consider defining monthly or daily partitions to enable aging and maintenance policies.
    PARTITION p_default VALUES LESS THAN (MAXVALUE)
);

CREATE INDEX idx_execution_plans_hash ON audit_graphql_execution_plans(query_hash, sampled_at DESC);
CREATE INDEX idx_execution_plans_anomaly ON audit_graphql_execution_plans(anomaly_detected, sampled_at DESC);

-- Circuit Breaker Events Table (for resilience tracking)
CREATE TABLE audit_circuit_breaker_events (
    id NUMBER(19) GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    breaker_name VARCHAR2(100) NOT NULL,
    service_name VARCHAR2(100) NOT NULL,
    state_transition VARCHAR2(50) NOT NULL, -- CLOSED->OPEN, OPEN->HALF_OPEN, etc.
    failure_rate NUMBER(5,2) NOT NULL CONSTRAINT chk_failure_rate CHECK (failure_rate >= 0 AND failure_rate <= 100),
    slow_call_rate NUMBER(5,2) NOT NULL CONSTRAINT chk_slow_call_rate CHECK (slow_call_rate >= 0 AND slow_call_rate <= 100),
    failure_reason CLOB,
    event_timestamp TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL
) PARTITION BY RANGE (event_timestamp) (
    -- Note: Using a single default partition for simplicity. For production environments with high data volumes,
    -- consider defining monthly or daily partitions to enable aging and maintenance policies.
    PARTITION p_default VALUES LESS THAN (MAXVALUE)
);

CREATE INDEX idx_circuit_breaker_name ON audit_circuit_breaker_events(breaker_name, event_timestamp DESC);
CREATE INDEX idx_circuit_breaker_service ON audit_circuit_breaker_events(service_name, state_transition, event_timestamp DESC);

-- HTTP Response Compression Metrics Table
CREATE TABLE audit_http_compression (
    id NUMBER(19) GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    content_type VARCHAR2(100),
    compression_algorithm VARCHAR2(20) NOT NULL, -- GZIP, BROTLI, NONE
    original_size NUMBER(19),
    compressed_size NUMBER(19),
    compression_ratio NUMBER(5,2) NOT NULL CONSTRAINT chk_compression_ratio CHECK (compression_ratio >= 0 AND compression_ratio <= 100),
    cpu_time_ms NUMBER(10),
    endpoint_path VARCHAR2(500),
    recorded_at TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL
) PARTITION BY RANGE (recorded_at) (
    -- Note: Using a single default partition for simplicity. For production environments with high data volumes,
    -- consider defining monthly or daily partitions to enable aging and maintenance policies.
    PARTITION p_default VALUES LESS THAN (MAXVALUE)
);

CREATE INDEX idx_http_compression_algo ON audit_http_compression(compression_algorithm, recorded_at DESC);
CREATE INDEX idx_http_compression_endpoint ON audit_http_compression(endpoint_path, compression_algorithm);

-- Grant privileges
GRANT SELECT, INSERT, UPDATE, DELETE ON persisted_queries TO app_user;
GRANT SELECT, INSERT ON audit_graphql_complexity TO app_user;
GRANT SELECT, INSERT ON audit_graphql_execution_plans TO app_user;
GRANT SELECT, INSERT ON audit_circuit_breaker_events TO app_user;
GRANT SELECT, INSERT ON audit_http_compression TO app_user;
