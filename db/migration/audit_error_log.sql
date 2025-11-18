--------------------------------------------------------------------------------
-- AUDIT_ERROR_LOG: Error Tracking for Debugging & Compliance
--------------------------------------------------------------------------------
-- Purpose:
--   Centralized error log for all application errors, exceptions, and failures
--   occurring in stored procedures, APIs, or services. Used for root cause
--   analysis, compliance audits, and operational alerting.
--
-- Retention Policy:
--   - Production: 1 year (365 days) on hot storage, then archival to cold storage
--   - Non-Prod: 90 days; older records may be purged
--   - Archives maintained separately per compliance requirements
--
-- Archival/Purge Strategy:
--   - Manual: Run infra/cronjobs/archive-audit-errors.sh monthly to move records
--     older than retention window to AUDIT_ERROR_LOG_ARCHIVE table
--   - Automated: Configure scheduled job (see infra/cronjobs/README.md) to:
--     INSERT INTO audit_error_log_archive SELECT * FROM audit_error_log 
--       WHERE created_at < TRUNC(SYSDATE) - {retention_days}
--     DELETE FROM audit_error_log WHERE created_at < TRUNC(SYSDATE) - {retention_days}
--   - Partitioning: Consider monthly partitions (RANGE partitioning on created_at)
--     if table exceeds 100M rows; see sql/partitioning/error_log_partitions.sql
--
-- Alerting & Monitoring:
--   - Configure alerts in ops/runbooks/error-log-alerts.md for:
--     * Error rate spike (>100 errors/minute for 5 minutes)
--     * Repeated error codes (same error_code >10 times in 1 hour)
--     * CRITICAL error_level entries (see error_level column)
--     * Failed auth attempts (error_code = 'AUTH_FAILED')
--   - Grafana dashboard: http://localhost:3000/d/error-logs
--   - PagerDuty escalation: ops-team@company.com
--
-- Oracle Compatibility:
--   Uses GENERATED ALWAYS AS IDENTITY (Oracle 12c+) for automatic PK generation.
--   Compatible with Oracle XE 21c.
--
CREATE TABLE audit_error_log (
	id NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
	error_code VARCHAR2(50),
	error_message VARCHAR2(4000),
	context VARCHAR2(4000),
	procedure_name VARCHAR2(128),
	stack_trace CLOB,
	user_id NUMBER(19),
	session_id NUMBER(19),
	error_level VARCHAR2(20),
	created_at TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
	CONSTRAINT chk_audit_error_log_level CHECK (error_level IN ('INFO', 'WARN', 'ERROR', 'CRITICAL')),
	CONSTRAINT fk_audit_error_log_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL,
	CONSTRAINT fk_audit_error_log_session FOREIGN KEY (session_id) REFERENCES audit_sessions(id) ON DELETE SET NULL
);

-- Primary retrieval indexes for filtering and time-range queries
-- NOTE: Index strategy consolidates composite indexes to eliminate redundant single-column indexes.
-- Single-column indexes on error_code, created_at, and procedure_name have been removed as they are
-- subsumed by composite indexes below (e.g., idx_audit_error_log_error_created covers both error_code
-- and created_at queries). All queries must specify a leading column matching a composite leftmost.
-- If profiling shows pure single-column lookups (e.g., error_code without time filter) have <5% of traffic,
-- reintroduce single-column indexes and document findings. Current composites cover the primary patterns:
-- - error spike queries (error_code + created_at filter)
-- - critical error alerts (error_level + created_at window)
-- - user session correlation (user_id + session_id)

-- Composite index for error spike detection and time-window analysis
CREATE INDEX idx_audit_error_log_error_created ON audit_error_log(error_code, created_at);

-- Correlation indexes for linking errors to user sessions
CREATE INDEX idx_audit_error_log_user_id ON audit_error_log(user_id);
CREATE INDEX idx_audit_error_log_session_id ON audit_error_log(session_id);
CREATE INDEX idx_audit_error_log_user_session ON audit_error_log(user_id, session_id);

-- Alerting index: efficient queries for critical errors in time windows
CREATE INDEX idx_audit_error_log_level_created ON audit_error_log(error_level, created_at);