package com.rcs.ssf.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

/**
 * Compliance metrics service that exposes Prometheus metrics for security and
 * compliance monitoring.
 * Registers gauges for MFA enrollment rate, audit log completeness, encryption
 * coverage, SOX control status, and counters for failed login attempts.
 */
@Component
public class ComplianceMetricsService {

    private final MeterRegistry meterRegistry;

    private volatile double mfaEnrollmentRate = 0.0;
    private volatile double auditLogCompleteness = 0.0;
    private volatile double encryptionCoverage = 0.0;
    private volatile double soxControlStatus = 0.0;

    private Counter successfulLoginCounter;
    private Counter logoutCounter;

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

        // Counter for successful login attempts
        successfulLoginCounter = Counter.builder("ssf_successful_login_attempts_total")
                .description("Total number of successful login attempts")
                .register(meterRegistry);

        // Counter for logout events
        logoutCounter = Counter.builder("ssf_logout_attempts_total")
                .description("Total number of logout attempts")
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

    /**
     * Increment the failed login attempts counter with a specific reason.
     * Uses tagged counters for detailed breakdown by failure reason (e.g., INVALID_CREDENTIALS, MFA_FAILED).
     * Note: This creates a new counter per unique tag combination; for high-cardinality reasons,
     * consider caching counters to avoid metric explosion.
     */
    public void incrementFailedLoginAttempts(String reason) {
        meterRegistry.counter("ssf_failed_login_attempts_total", "reason", reason != null ? reason : "UNKNOWN").increment();
    }

    /**
     * Increment the successful login attempts counter.
     * Uses a pre-registered counter for consistency and to avoid per-request counter creation overhead.
     * Unlike failed attempts, successful logins don't require reason-based tagging as they are uniform.
     */
    public void incrementSuccessfulLoginAttempts() {
        if (successfulLoginCounter != null) {
            successfulLoginCounter.increment();
        }
    }

    /**
     * Increment the logout attempts counter.
     * Uses a pre-registered counter for consistency and to track user session termination events.
     */
    public void incrementLogoutAttempts() {
        if (logoutCounter != null) {
            logoutCounter.increment();
        }
    }
}
