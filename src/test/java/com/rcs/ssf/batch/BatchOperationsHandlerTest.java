package com.rcs.ssf.batch;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("BatchOperationsHandler Tests")
class BatchOperationsHandlerTest {

    private BatchOperationsHandler batchHandler;

    @BeforeEach
    void setUp() {
        batchHandler = new BatchOperationsHandler();
        
        // Set batch size to 50 for testing
        ReflectionTestUtils.setField(batchHandler, "batchSize", 50);
        ReflectionTestUtils.setField(batchHandler, "maxRetries", 3);
        ReflectionTestUtils.setField(batchHandler, "initialRetryDelayMs", 10L);
        // Set very high memory threshold (99%) so tests don't trigger memory pressure warnings
        ReflectionTestUtils.setField(batchHandler, "memoryThresholdPercent", 99);
    }

    @Test
    @DisplayName("Should execute batch operations successfully")
    void testExecuteBatchSuccess() {
        List<Integer> items = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            items.add(i);
        }
        
        AtomicInteger processedCount = new AtomicInteger(0);
        
        BatchOperationsHandler.BatchResult result = batchHandler.executeBatch(
            items,
            batch -> processedCount.addAndGet(batch.size()),
            "test_operation"
        );
        
        assertTrue(result.success());
        assertEquals(100, result.processedItems());
        assertEquals(0, result.failedItems());
    }

    @Test
    @DisplayName("Should use individual operations for small batches (< 50 items)")
    void testSmallBatchUsesIndividualOperations() {
        List<Integer> items = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            items.add(i);
        }
        
        AtomicInteger callCount = new AtomicInteger(0);
        
        BatchOperationsHandler.BatchResult result = batchHandler.executeBatch(
            items,
            batch -> callCount.incrementAndGet(),
            "small_batch_operation"
        );
        
        assertTrue(result.success());
        assertEquals(30, result.processedItems());
        // Should be called 30 times (individual operations)
        assertEquals(30, callCount.get());
    }

    @Test
    @DisplayName("Should batch operations for large batches (>= 50 items)")
    void testLargeBatchUsesBatching() {
        List<Integer> items = new ArrayList<>();
        for (int i = 0; i < 150; i++) {
            items.add(i);
        }
        
        AtomicInteger callCount = new AtomicInteger(0);
        
        BatchOperationsHandler.BatchResult result = batchHandler.executeBatch(
            items,
            batch -> callCount.incrementAndGet(),
            "large_batch_operation"
        );
        
        assertTrue(result.success());
        assertEquals(150, result.processedItems());
        // With batch size 50, should be called 3 times (150/50)
        assertEquals(3, callCount.get());
    }

    @Test
    @DisplayName("Should retry failed batches with exponential backoff")
    void testRetryWithExponentialBackoff() {
        // Use 25 items to ensure single batch (batchSize=50)
        // This isolates retry behavior to one batch
        List<Integer> items = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            items.add(i);
        }
        
        AtomicInteger attemptCount = new AtomicInteger(0);
        
        BatchOperationsHandler.BatchResult result = batchHandler.executeBatch(
            items,
            batch -> {
                attemptCount.incrementAndGet();
                if (attemptCount.get() < 3) {
                    throw new RuntimeException("Simulated failure");
                }
            },
            "retry_operation"
        );
        
        assertTrue(result.success());
        assertEquals(25, result.processedItems());
        assertEquals(3, attemptCount.get()); // Should succeed on 3rd attempt (batch 1: fail, fail, succeed)
    }

    @Test
    @DisplayName("Should throw exception after max retries exceeded")
    void testExceptionAfterMaxRetriesExceeded() {
        List<Integer> items = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            items.add(i);
        }
        
        assertThrows(BatchOperationException.class, () -> 
            batchHandler.executeBatch(
                items,
                batch -> {
                    throw new RuntimeException("Permanent failure");
                },
                "failing_operation"
            )
        );
    }

    @Test
    @DisplayName("Should split items into batches correctly")
    void testCreateBatches() {
        List<Integer> items = new ArrayList<>();
        for (int i = 0; i < 175; i++) {
            items.add(i);
        }
        
        ReflectionTestUtils.setField(batchHandler, "batchSize", 50);
        
        List<List<Integer>> batches = batchHandler.createBatches(items);
        
        assertEquals(4, batches.size()); // 175 / 50 = 3 full batches + 1 partial
        assertEquals(50, batches.get(0).size());
        assertEquals(50, batches.get(1).size());
        assertEquals(50, batches.get(2).size());
        assertEquals(25, batches.get(3).size());
    }

    @Test
    @DisplayName("Should handle empty batch gracefully")
    void testEmptyBatch() {
        List<Integer> items = new ArrayList<>();
        
        BatchOperationsHandler.BatchResult result = batchHandler.executeBatch(
            items,
            batch -> {},
            "empty_operation"
        );
        
        assertTrue(result.success());
        assertEquals(0, result.processedItems());
        assertEquals(0, result.failedItems());
    }

    @Test
    @DisplayName("Should throw exception for null items")
    void testNullItemsThrowException() {
        assertThrows(NullPointerException.class, () -> 
            batchHandler.executeBatch(null, batch -> {}, "test")
        );
    }

    @Test
    @DisplayName("Should throw exception for null operation")
    void testNullOperationThrowsException() {
        assertThrows(NullPointerException.class, () -> 
            batchHandler.executeBatch(new ArrayList<>(), null, "test")
        );
    }

}
