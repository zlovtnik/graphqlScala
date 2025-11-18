package com.rcs.ssf.metrics;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

/**
 * Compliance metrics service that exposes Prometheus metrics for security and
 * compliance monitoring.
 * Registers gauges for MFA enrollment rate, audit log completeness, encryption
 * coverage, and SOX control status.
 */
@Component
public class ComplianceMetricsService {

    private final MeterRegistry meterRegistry;

    private volatile double mfaEnrollmentRate = 0.0;
    private volatile double auditLogCompleteness = 0.0;
    private volatile double encryptionCoverage = 0.0;
    private volatile double soxControlStatus = 0.0;

    public ComplianceMetricsService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @PostConstruct
    public void registerMetrics() {
        Gauge.builder("ssf_mfa_enrollment_rate", () -> mfaEnrollmentRate)
                .description("MFA enrollment rate as a percentage")
                .register(meterRegistry);

        Gauge.builder("ssf_audit_log_completeness", () -> auditLogCompleteness)
                .description("Audit log completeness as a percentage")
                .register(meterRegistry);

        Gauge.builder("ssf_encryption_coverage", () -> encryptionCoverage)
                .description("Encryption coverage as a percentage")
                .register(meterRegistry);

        Gauge.builder("ssf_sox_control_status", () -> soxControlStatus)
                .description("SOX control status as a percentage")
                .register(meterRegistry);
    }

    /**
     * Update MFA enrollment rate (0.0 to 1.0).
     */
    public void setMfaEnrollmentRate(double rate) {
        mfaEnrollmentRate = Math.max(0.0, Math.min(1.0, rate));
    }

    /**
     * Update audit log completeness (0.0 to 1.0).
     */
    public void setAuditLogCompleteness(double completeness) {
        auditLogCompleteness = Math.max(0.0, Math.min(1.0, completeness));
    }

    /**
     * Update encryption coverage (0.0 to 1.0).
     */
    public void setEncryptionCoverage(double coverage) {
        encryptionCoverage = Math.max(0.0, Math.min(1.0, coverage));
    }

    /**
     * Update SOX control status (0.0 to 1.0).
     */
    public void setSoxControlStatus(double status) {
        soxControlStatus = Math.max(0.0, Math.min(1.0, status));
    }
}
