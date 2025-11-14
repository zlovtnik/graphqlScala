package com.rcs.ssf.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Records metrics for batch operations including throughput, error rates, and performance.
 * Integrates with Micrometer/Prometheus for monitoring.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class BatchMetricsRecorder {

    private final MeterRegistry meterRegistry;
    private final ConcurrentMap<String, BatchMetrics> metricsCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Timer> durationTimers = new ConcurrentHashMap<>();
    
    // Persistent gauge references keyed by operation name
    private final ConcurrentMap<String, AtomicReference<Double>> throughputGauges = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, AtomicReference<Double>> memoryGauges = new ConcurrentHashMap<>();

    /**
     * Record successful batch completion using cached meters and persistent gauge references.
     */
    public void recordBatchCompletion(String operationName, int processedItems, int failedItems, 
                                     long durationMs, double throughput) {
        try {
            // Use convenience methods to get or create counters
            meterRegistry.counter("batch.items.processed", "operation", operationName)
                .increment(processedItems);

            meterRegistry.counter("batch.items.failed", "operation", operationName)
                .increment(failedItems);

            // Use cached timer configured with percentiles
            Timer timer = durationTimers.computeIfAbsent(operationName, op -> Timer.builder("batch.duration")
                .tags("operation", op)
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(meterRegistry));
            timer.record(durationMs, TimeUnit.MILLISECONDS);

            // Use persistent AtomicReference for throughput gauge
            throughputGauges.computeIfAbsent(operationName, op -> {
                AtomicReference<Double> gaugeRef = new AtomicReference<>(throughput);
                meterRegistry.gauge(
                    "batch.throughput.items_per_sec",
                    io.micrometer.core.instrument.Tags.of("operation", operationName),
                    gaugeRef,
                    AtomicReference::get
                );
                return gaugeRef;
            }).set(throughput);

            // Update cache
            BatchMetrics metrics = metricsCache.computeIfAbsent(operationName, k -> new BatchMetrics());
            metrics.recordSuccess(processedItems, failedItems, durationMs, throughput);

            log.debug("Recorded batch metrics for operation '{}': processed={}, failed={}, throughput={:.2f}/sec",
                operationName, processedItems, failedItems, throughput);

        } catch (Exception e) {
            log.error("Failed to record batch completion metrics for operation '{}': {}", operationName, e.getMessage());
        }
    }

    /**
     * Record individual operations (when batch size < 50) using cached meters.
     */
    public void recordIndividualOperations(String operationName, int processedItems, int failedItems,
                                          long durationMs, double throughput) {
        try {
            // Use convenience method to get or create counter
            meterRegistry.counter("batch.individual.operations", "operation", operationName)
                .increment(processedItems);

            // Use convenience method to get or create timer
            meterRegistry.timer("batch.individual.duration", "operation", operationName,
                "percentiles", "0.5,0.95,0.99")
                .record(durationMs, java.util.concurrent.TimeUnit.MILLISECONDS);

            log.debug("Recorded individual operation metrics for '{}': processed={}, failed={}, throughput={:.2f}/sec",
                operationName, processedItems, failedItems, throughput);

        } catch (Exception e) {
            log.error("Failed to record individual operation metrics for '{}': {}", operationName, e.getMessage());
        }
    }

    /**
     * Record batch operation error using cached meters.
     */
    public void recordBatchError(String operationName, int failedItems, Exception error) {
        try {
            // Use convenience method to get or create error counter
            meterRegistry.counter("batch.errors", 
                "operation", operationName,
                "error_type", error.getClass().getSimpleName())
                .increment(1);

            // Use convenience method to increment failed items counter
            meterRegistry.counter("batch.items.failed", "operation", operationName)
                .increment(failedItems);

            log.debug("Recorded batch error metrics for operation '{}': failed_items={}, error={}",
                operationName, failedItems, error.getClass().getSimpleName());

        } catch (Exception e) {
            log.error("Failed to record batch error metrics for '{}': {}", operationName, e.getMessage());
        }
    }

    /**
     * Record memory pressure event using persistent gauge references.
     */
    public void recordMemoryPressure(String operationName, long usedMemory, long maxMemory) {
        try {
            double usagePercent = (usedMemory * 100.0) / maxMemory;
            
            // Use persistent AtomicReference for memory gauge
            memoryGauges.computeIfAbsent(operationName, op -> {
                AtomicReference<Double> gaugeRef = new AtomicReference<>(usagePercent);
                meterRegistry.gauge(
                    "batch.memory.pressure.percent",
                    io.micrometer.core.instrument.Tags.of("operation", operationName),
                    gaugeRef,
                    AtomicReference::get
                );
                return gaugeRef;
            }).set(usagePercent);

            log.debug("Recorded memory pressure for operation '{}': {:.1f}%", operationName, usagePercent);

        } catch (Exception e) {
            log.error("Failed to record memory pressure metrics: {}", e.getMessage());
        }
    }

    /**
     * Get aggregated metrics for an operation.
     */
    public BatchMetrics getMetrics(String operationName) {
        return metricsCache.getOrDefault(operationName, new BatchMetrics());
    }

    /**
     * Batch operation metrics holder.
     */
    @lombok.Data
    public static class BatchMetrics {
        private long totalProcessed = 0;
        private long totalFailed = 0;
        private long totalDurationMs = 0;
        private double averageThroughput = 0;
        private int executionCount = 0;

        public void recordSuccess(int processedItems, int failedItems, long durationMs, double throughput) {
            this.totalProcessed += processedItems;
            this.totalFailed += failedItems;
            this.totalDurationMs += durationMs;
            this.executionCount++;
            this.averageThroughput = (this.totalProcessed * 1000.0) / Math.max(this.totalDurationMs, 1);
        }

        public double getErrorRate() {
            if (totalProcessed + totalFailed == 0) {
                return 0;
            }
            return (totalFailed * 100.0) / (totalProcessed + totalFailed);
        }

        public double getAverageDurationMs() {
            if (executionCount == 0) {
                return 0;
            }
            return (double) totalDurationMs / executionCount;
        }
    }

}
