-- Indexes for audit_error_log
CREATE INDEX idx_audit_error_log_timestamp ON audit_error_log(timestamp);
CREATE INDEX idx_audit_error_log_error_code ON audit_error_log(error_code);
CREATE INDEX idx_audit_error_log_procedure_name ON audit_error_log(procedure_name);
CREATE INDEX idx_audit_error_log_error_timestamp ON audit_error_log(error_code, timestamp);