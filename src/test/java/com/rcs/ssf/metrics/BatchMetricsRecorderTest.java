package com.rcs.ssf.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("BatchMetricsRecorder Tests")
class BatchMetricsRecorderTest {

    private BatchMetricsRecorder metricsRecorder;
    private MeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        metricsRecorder = new BatchMetricsRecorder(meterRegistry);
    }

    @Test
    @DisplayName("Should record batch completion metrics")
    void testRecordBatchCompletion() {
        metricsRecorder.recordBatchCompletion("test_op", 100, 0, 1000, 100.0);
        
        BatchMetricsRecorder.BatchMetrics metrics = metricsRecorder.getMetrics("test_op");
        
        assertEquals(100, metrics.getTotalProcessed());
        assertEquals(0, metrics.getTotalFailed());
        assertEquals(1000, metrics.getTotalDurationMs());
        assertEquals(1, metrics.getExecutionCount());
    }

    @Test
    @DisplayName("Should calculate error rate correctly")
    void testErrorRateCalculation() {
        metricsRecorder.recordBatchCompletion("test_op", 80, 20, 1000, 80.0);
        
        BatchMetricsRecorder.BatchMetrics metrics = metricsRecorder.getMetrics("test_op");
        
        assertEquals(20.0, metrics.getErrorRate(), 0.01);
    }

    @Test
    @DisplayName("Should calculate average duration correctly")
    void testAverageDurationCalculation() {
        metricsRecorder.recordBatchCompletion("test_op", 100, 0, 1000, 100.0);
        metricsRecorder.recordBatchCompletion("test_op", 100, 0, 1000, 100.0);
        
        BatchMetricsRecorder.BatchMetrics metrics = metricsRecorder.getMetrics("test_op");
        
        assertEquals(1000.0, metrics.getAverageDurationMs(), 0.01);
    }

    @Test
    @DisplayName("Should record individual operations")
    void testRecordIndividualOperations() {
        metricsRecorder.recordIndividualOperations("individual_op", 50, 0, 500, 100.0);
        
        BatchMetricsRecorder.BatchMetrics metrics = metricsRecorder.getMetrics("individual_op");
        
        assertEquals(50, metrics.getTotalProcessed());
        assertEquals(500, metrics.getTotalDurationMs());
    }

    @Test
    @DisplayName("Should record batch errors")
    void testRecordBatchError() {
        Exception error = new RuntimeException("Test error");
        metricsRecorder.recordBatchError("error_op", 10, error);
        
        // Should not throw exception, just log
        assertDoesNotThrow(() -> metricsRecorder.recordBatchError("error_op", 10, error));
    }

    @Test
    @DisplayName("Should record memory pressure")
    void testRecordMemoryPressure() {
        metricsRecorder.recordMemoryPressure("memory_op", 800000000, 1000000000);
        
        // Should not throw exception
        assertDoesNotThrow(() -> metricsRecorder.recordMemoryPressure("memory_op", 800000000, 1000000000));
    }

    @Test
    @DisplayName("Should initialize metrics with zero values")
    void testMetricsInitialization() {
        BatchMetricsRecorder.BatchMetrics metrics = metricsRecorder.getMetrics("nonexistent");
        
        assertEquals(0, metrics.getTotalProcessed());
        assertEquals(0, metrics.getTotalFailed());
        assertEquals(0.0, metrics.getErrorRate());
        assertEquals(0.0, metrics.getAverageDurationMs());
    }

}
