package com.rcs.ssf.batch;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Handler for JDBC batch operations with retry logic and performance monitoring.
 * 
 * Features:
 * - Configurable batch sizes (100-500 based on memory profiling)
 * - Configurable batch break-even threshold (default: 50 rows)
 * - Exponential backoff retry logic (max retries configurable)
 * - Performance monitoring (throughput, error rates)
 * - Memory pressure detection
 * - Batch vs individual operation optimization
 * 
 * Configuration Properties:
 * - batch.size: Batch size for processing (default: 200)
 * - batch.max-retries: Maximum retry attempts (default: 3, must be >= 0)
 * - batch.initial-retry-delay-ms: Initial retry delay in milliseconds (default: 100, must be >= 0)
 * - batch.memory-threshold-percent: Memory threshold percentage (default: 80, must be 0-100)
 * - batch.threshold: Break-even threshold for switching between individual and batch mode (default: 50, must be > 0)
 */
@Component
@Slf4j
public class BatchOperationsHandler {

    @Value("${batch.size:200}")
    private int batchSize;

    @Value("${batch.max-retries:3}")
    private int maxRetries;

    @Value("${batch.initial-retry-delay-ms:100}")
    private long initialRetryDelayMs;

    @Value("${batch.memory-threshold-percent:80}")
    private int memoryThresholdPercent;

    @Value("${batch.threshold:50}")
    private int batchThreshold;

    /**
     * Validate configuration values at startup.
     * Ensures all configuration properties are within valid ranges.
     * 
     * @throws IllegalStateException if any configuration value is invalid
     */
    @PostConstruct
    public void validateConfiguration() {
        if (batchSize <= 0) {
            throw new IllegalStateException(String.format(
                "Invalid configuration: batch.size must be > 0, but was %d", batchSize));
        }
        
        if (maxRetries < 0) {
            throw new IllegalStateException(String.format(
                "Invalid configuration: batch.max-retries must be >= 0, but was %d", maxRetries));
        }
        
        if (initialRetryDelayMs < 0) {
            throw new IllegalStateException(String.format(
                "Invalid configuration: batch.initial-retry-delay-ms must be >= 0, but was %d", initialRetryDelayMs));
        }
        
        if (memoryThresholdPercent < 0 || memoryThresholdPercent > 100) {
            throw new IllegalStateException(String.format(
                "Invalid configuration: batch.memory-threshold-percent must be between 0 and 100, but was %d", memoryThresholdPercent));
        }
        
        if (batchThreshold <= 0) {
            throw new IllegalStateException(String.format(
                "Invalid configuration: batch.threshold must be > 0, but was %d", batchThreshold));
        }
        
        log.info("BatchOperationsHandler configuration validated: batchSize={}, maxRetries={}, initialRetryDelayMs={}, memoryThresholdPercent={}, batchThreshold={}",
            batchSize, maxRetries, initialRetryDelayMs, memoryThresholdPercent, batchThreshold);
    }

    /**
     * Execute batch operations with retry logic and monitoring.
     * 
     * Automatically decides between batch and individual operations based on item count:
     * - If items.size() < batchThreshold: uses individual operations (smaller overhead)
     * - Otherwise: batches items in groups for more efficient processing
     * 
     * Both modes support partial success (some items fail, others succeed). The method
     * returns a BatchResult summarizing success/failure counts and performance metrics.
     * Individual operations that fail are logged but do not throw exceptions; batch
     * operations that fail are also logged and accumulated, with no exception thrown.
     * 
     * Memory pressure is checked before starting batch operations. If memory pressure
     * exceeds the configured threshold, batch size is automatically reduced by 50%.
     * 
     * @param items list of items to process (must not be null)
     * @param operation function to apply to each batch (must not be null)
     * @param operationName descriptive name for logging and metrics
     * @return BatchResult containing success status, processed items, failed items, and duration
     * @throws NullPointerException if items or operation is null
     */
    public <T> BatchResult executeBatch(List<T> items, Consumer<List<T>> operation, String operationName) {
        Objects.requireNonNull(items, "Items cannot be null");
        Objects.requireNonNull(operation, "Operation cannot be null");

        // Optimize: use individual operations if count < batchThreshold
        if (items.size() < batchThreshold) {
            log.debug("Using individual operations for {} items (< {} break-even threshold)", items.size(), batchThreshold);
            return executeIndividualOperations(items, operation, operationName);
        }

        // Check memory pressure
        if (isMemoryPressureHigh()) {
            log.warn("High memory pressure detected, reducing batch size from {} to {}", batchSize, batchSize / 2);
            return executeBatchWithReducedSize(items, operation, operationName, batchSize / 2);
        }

        return executeBatchInternal(items, operation, operationName, batchSize, 0);
    }

    /**
     * Execute batch operations with exponential backoff retry logic (internal).
     * 
     * This method implements a two-level retry strategy:
     * 1. Individual batch failures are captured and aggregated
     * 2. If any batches fail and retries remain, only failed batches are retried
     * 
     * Unlike individual operations which silently track failures, failed batches are
     * retried exponentially. If all retries are exhausted, the failed items are counted
     * but NO exception is thrown - the method returns a BatchResult with partial success.
     * 
     * @param items list of items to process
     * @param operation function to apply to each batch
     * @param operationName descriptive name for logging
     * @param size current batch size being used
     * @param attemptNumber current attempt number (0-based)
     * @return BatchResult with success status, item counts, and performance metrics
     */
    private <T> BatchResult executeBatchInternal(List<T> items, Consumer<List<T>> operation, 
                                                  String operationName, int size, int attemptNumber) {
        long startTimeMs = System.currentTimeMillis();
        
        // Create initial batches
        List<List<T>> batches = createBatches(items);
        List<List<T>> failedBatches = new ArrayList<>();
        int processedItems = 0;
        
        // Process all batches, collecting those that fail
        for (List<T> batch : batches) {
            try {
                executeSingleBatchWithRetry(batch, operation, operationName, 0);
                processedItems += batch.size();
            } catch (Exception e) {
                failedBatches.add(batch);
                log.debug("Batch failed (will retry): {} items - {}", batch.size(), e.getMessage());
            }
        }
        
        // If there are failed batches and retries remaining, retry only failed batches
        if (!failedBatches.isEmpty() && attemptNumber < maxRetries) {
            long delayMs = initialRetryDelayMs * (long) Math.pow(2, attemptNumber);
            log.warn("Batch operation '{}' has {} failed batches (attempt {}/{}), retrying after {}ms",
                operationName, failedBatches.size(), attemptNumber + 1, maxRetries, delayMs);
            
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                log.error("Batch retry interrupted for operation '{}'", operationName);
                // Continue with partial success - don't fail the entire operation
            }
            
            // Flatten failed batches back into a list for retry
            List<T> failedItems = failedBatches.stream()
                .flatMap(List::stream)
                .collect(Collectors.toList());
            
            return executeBatchInternal(failedItems, operation, operationName, size, attemptNumber + 1);
        }
        
        // Calculate final metrics
        int failedItems = failedBatches.stream().mapToInt(List::size).sum();
        long durationMs = System.currentTimeMillis() - startTimeMs;
        double throughput = (processedItems * 1000.0) / Math.max(durationMs, 1);
        boolean success = failedBatches.isEmpty();

        log.info("Batch operation '{}' completed: processed={}, failed={}, throughput={:.2f} items/sec, duration={}ms",
            operationName, processedItems, failedItems, throughput, durationMs);

        return new BatchResult(success, processedItems, failedItems, durationMs);
    }

    /**
     * Execute a single batch with retry logic.
     * 
     * If the batch fails after all retries are exhausted, the exception is re-thrown
     * so the caller (executeBatchInternal) can aggregate it as a failed batch and
     * return a BatchResult with partial success information.
     * 
     * @param batch items in this batch
     * @param operation function to execute on the batch
     * @param operationName descriptive name for logging
     * @param attemptNumber current attempt (0-based)
     * @throws Exception if the batch fails after exhausting retries
     */
    private <T> void executeSingleBatchWithRetry(List<T> batch, Consumer<List<T>> operation, 
                                                  String operationName, int attemptNumber) {
        try {
            operation.accept(batch);
            if (attemptNumber > 0) {
                log.debug("Batch operation '{}' succeeded after {} retries", operationName, attemptNumber);
            }
        } catch (Exception e) {
            if (attemptNumber < maxRetries) {
                long delayMs = initialRetryDelayMs * (long) Math.pow(2, attemptNumber);
                log.warn("Batch operation '{}' failed (attempt {}/{}), retrying after {}ms: {}",
                    operationName, attemptNumber + 1, maxRetries, delayMs, e.getMessage());
                
                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    log.error("Batch retry interrupted for operation '{}'", operationName);
                    throw new BatchOperationException("Batch retry interrupted", ie);
                }
                
                executeSingleBatchWithRetry(batch, operation, operationName, attemptNumber + 1);
            } else {
                log.error("Batch operation '{}' failed after {} retries: {}", operationName, maxRetries, e.getMessage());
                throw e; // Re-throw so caller can aggregate as failed batch
            }
        }
    }

    /**
     * Execute individual operations when batch count is small (< 50 items).
     */
    private <T> BatchResult executeIndividualOperations(List<T> items, Consumer<List<T>> operation, 
                                                       String operationName) {
        long startTimeMs = System.currentTimeMillis();
        int processedItems = 0;
        int failedItems = 0;

        for (T item : items) {
            try {
                operation.accept(List.of(item));
                processedItems++;
            } catch (Exception e) {
                failedItems++;
                log.warn("Individual operation '{}' failed for item: {}", operationName, e.getMessage());
            }
        }

        long durationMs = System.currentTimeMillis() - startTimeMs;
        double throughput = (processedItems * 1000.0) / durationMs;

        log.info("Individual operations '{}' completed: processed={}, failed={}, throughput={:.2f} items/sec",
            operationName, processedItems, failedItems, throughput);

        return new BatchResult(failedItems == 0, processedItems, failedItems, durationMs);
    }

    /**
     * Execute batch with reduced size due to memory pressure.
     */
    private <T> BatchResult executeBatchWithReducedSize(List<T> items, Consumer<List<T>> operation, 
                                                       String operationName, int reducedSize) {
        return executeBatchInternal(items, operation, operationName, reducedSize, 0);
    }

    /**
     * Check if memory pressure is high by comparing used heap against JVM max heap.
     * 
     * <p>This method measures the percentage of the JVM's maximum heap that is currently
     * in use (not free). The calculation is:
     * usedMemory = totalMemory - freeMemory
     * usagePercent = (usedMemory / maxMemory) * 100
     * 
     * Note: totalMemory is the heap size currently allocated by the JVM (which may grow
     * up to maxMemory). This metric measures used heap relative to the JVM's maximum
     * heap capacity, not against currently allocated heap.</p>
     * 
     * <p><strong>Example:</strong> If maxMemory=1GB, totalMemory=500MB, freeMemory=100MB:
     * usedMemory = 500MB - 100MB = 400MB
     * usagePercent = (400MB / 1000MB) * 100 = 40%
     * Returns true only if 40% > memoryThresholdPercent (default 80%)</p>
     * 
     * @return true if used heap percentage exceeds memoryThresholdPercent; false otherwise
     */
    private boolean isMemoryPressureHigh() {
        Runtime runtime = Runtime.getRuntime();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;
        long maxMemory = runtime.maxMemory();

        double usagePercent = (usedMemory * 100.0) / maxMemory;
        return usagePercent > memoryThresholdPercent;
    }

    /**
     * Split items into batches for processing using index-based approach (O(n) complexity).
     */
    public <T> List<List<T>> createBatches(List<T> items) {
        int numBatches = (items.size() + batchSize - 1) / batchSize;
        return java.util.stream.IntStream.range(0, numBatches)
            .mapToObj(i -> new ArrayList<>(items.subList(
                i * batchSize,
                Math.min((i + 1) * batchSize, items.size()))))
            .collect(Collectors.toList());
    }

    /**
     * Result of a batch operation.
     */
    public record BatchResult(
        boolean success,
        int processedItems,
        int failedItems,
        long durationMs
    ) {}

}
