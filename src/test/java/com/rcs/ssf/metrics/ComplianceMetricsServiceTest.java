package com.rcs.ssf.metrics;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ComplianceMetricsService Tests")
class ComplianceMetricsServiceTest {

    private ComplianceMetricsService complianceMetricsService;
    private MeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        complianceMetricsService = new ComplianceMetricsService(meterRegistry);
        complianceMetricsService.registerMetrics();
    }

    @Test
    @DisplayName("Should register all compliance metrics gauges")
    void testMetricsRegistration() {
        assertNotNull(meterRegistry.find("ssf_mfa_enrollment_rate").gauge());
        assertNotNull(meterRegistry.find("ssf_audit_log_completeness").gauge());
        assertNotNull(meterRegistry.find("ssf_encryption_coverage").gauge());
        assertNotNull(meterRegistry.find("ssf_sox_control_status").gauge());
    }

    @Test
    @DisplayName("Should update MFA enrollment rate and reflect in gauge")
    void testMfaEnrollmentRateUpdate() {
        complianceMetricsService.setMfaEnrollmentRate(0.85);

        Gauge gauge = meterRegistry.find("ssf_mfa_enrollment_rate").gauge();
        assertEquals(0.85, gauge.value());
    }

    @ParameterizedTest
    @CsvSource({
        "mfaEnrollmentRate,      MFA Enrollment Rate,    ssf_mfa_enrollment_rate",
        "auditLogCompleteness,   Audit Log Completeness, ssf_audit_log_completeness",
        "encryptionCoverage,     Encryption Coverage,    ssf_encryption_coverage",
        "soxControlStatus,       SOX Control Status,     ssf_sox_control_status"
    })
    @DisplayName("Should clamp metric values to valid range [0.0, 1.0]")
    void testMetricClamping(String methodName, String metricDescription, String gaugeName) throws Exception {
        // Test clamping to minimum (0.0)
        invokeSetterMethod(methodName, -0.1);
        Gauge gauge = meterRegistry.find(gaugeName).gauge();
        assertEquals(0.0, gauge.value(), 
            metricDescription + " should clamp -0.1 to 0.0");

        // Test clamping to maximum (1.0)
        invokeSetterMethod(methodName, 1.5);
        assertEquals(1.0, gauge.value(), 
            metricDescription + " should clamp 1.5 to 1.0");
    }

    @Test
    @DisplayName("Should update audit log completeness and reflect in gauge")
    void testAuditLogCompletenessUpdate() {
        complianceMetricsService.setAuditLogCompleteness(0.92);

        Gauge gauge = meterRegistry.find("ssf_audit_log_completeness").gauge();
        assertEquals(0.92, gauge.value());
    }

    @Test
    @DisplayName("Should update encryption coverage and reflect in gauge")
    void testEncryptionCoverageUpdate() {
        complianceMetricsService.setEncryptionCoverage(0.78);

        Gauge gauge = meterRegistry.find("ssf_encryption_coverage").gauge();
        assertEquals(0.78, gauge.value());
    }

    @Test
    @DisplayName("Should update SOX control status and reflect in gauge")
    void testSoxControlStatusUpdate() {
        complianceMetricsService.setSoxControlStatus(0.95);

        Gauge gauge = meterRegistry.find("ssf_sox_control_status").gauge();
        assertEquals(0.95, gauge.value());
    }

    @Test
    @DisplayName("Should initialize all metrics to zero")
    void testInitialValues() {
        Gauge mfaGauge = meterRegistry.find("ssf_mfa_enrollment_rate").gauge();
        Gauge auditGauge = meterRegistry.find("ssf_audit_log_completeness").gauge();
        Gauge encryptionGauge = meterRegistry.find("ssf_encryption_coverage").gauge();
        Gauge soxGauge = meterRegistry.find("ssf_sox_control_status").gauge();

        assertEquals(0.0, mfaGauge.value());
        assertEquals(0.0, auditGauge.value());
        assertEquals(0.0, encryptionGauge.value());
        assertEquals(0.0, soxGauge.value());
    }

    /**
     * Helper method to invoke setter methods using reflection.
     * Dynamically calls the appropriate set* method on complianceMetricsService
     * based on the provided method name.
     *
     * @param methodName the name of the setter method (e.g., "mfaEnrollmentRate")
     * @param value      the value to set
     * @throws NoSuchMethodException if the setter method is not found
     * @throws IllegalAccessException if the method is not accessible
     * @throws InvocationTargetException if the method invocation fails
     */
    private void invokeSetterMethod(String methodName, double value)
            throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        // Construct the setter method name from camelCase field name
        String setterName = "set" + methodName.substring(0, 1).toUpperCase() + methodName.substring(1);
        Method method = ComplianceMetricsService.class.getMethod(setterName, double.class);
        method.invoke(complianceMetricsService, value);
    }

}
