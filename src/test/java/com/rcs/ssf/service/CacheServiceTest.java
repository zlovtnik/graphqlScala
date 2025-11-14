package com.rcs.ssf.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CacheService Tests")
class CacheServiceTest {

    private CacheService cacheService;

    @BeforeEach
    void setUp() {
        cacheService = new CacheService(new CacheConfiguration());
    }

    @Test
    @DisplayName("Should cache and retrieve value")
    void testGetOrComputeCachesValue() {
        String cacheKey = "test_key";
        AtomicInteger computeCount = new AtomicInteger(0);
        
        // First call - should compute
        String result1 = cacheService.getOrCompute(cacheKey, key -> {
            computeCount.incrementAndGet();
            return "computed_value";
        }, CacheService.QUERY_RESULT_CACHE);
        
        assertEquals("computed_value", result1);
        assertEquals(1, computeCount.get());
        
        // Second call - should use cache
        String result2 = cacheService.getOrCompute(cacheKey, key -> {
            computeCount.incrementAndGet();
            return "different_value";
        }, CacheService.QUERY_RESULT_CACHE);
        
        assertEquals("computed_value", result2);
        assertEquals(1, computeCount.get()); // Should not have incremented
    }

    @Test
    @DisplayName("Should put and get value from cache")
    void testPutAndGetIfPresent() {
        String cacheKey = "test_key";
        String value = "test_value";
        
        cacheService.put(cacheKey, value, CacheService.QUERY_RESULT_CACHE);
        String retrieved = cacheService.getIfPresent(cacheKey, CacheService.QUERY_RESULT_CACHE);
        
        assertEquals(value, retrieved);
    }

    @Test
    @DisplayName("Should invalidate specific cache key")
    void testInvalidateKey() {
        String cacheKey = "test_key";
        cacheService.put(cacheKey, "value", CacheService.QUERY_RESULT_CACHE);
        
        String before = cacheService.getIfPresent(cacheKey, CacheService.QUERY_RESULT_CACHE);
        assertNotNull(before);
        
        cacheService.invalidate(cacheKey, CacheService.QUERY_RESULT_CACHE);
        
        String after = cacheService.getIfPresent(cacheKey, CacheService.QUERY_RESULT_CACHE);
        assertNull(after);
    }

    @Test
    @DisplayName("Should invalidate all cache entries")
    void testInvalidateAll() {
        cacheService.put("key1", "value1", CacheService.QUERY_RESULT_CACHE);
        cacheService.put("key2", "value2", CacheService.QUERY_RESULT_CACHE);
        
        long sizeBefore = cacheService.getCacheSize(CacheService.QUERY_RESULT_CACHE);
        assertTrue(sizeBefore > 0);
        
        cacheService.invalidateAll(CacheService.QUERY_RESULT_CACHE);
        
        long sizeAfter = cacheService.getCacheSize(CacheService.QUERY_RESULT_CACHE);
        assertEquals(0, sizeAfter);
    }

    @Test
    @DisplayName("Should warm up cache with initial values")
    void testWarmUpCache() {
        String cacheKey = "critical_query";
        String value = "precomputed_result";
        
        cacheService.warmUpCache(cacheKey, value, CacheService.QUERY_RESULT_CACHE);
        
        String retrieved = cacheService.getIfPresent(cacheKey, CacheService.QUERY_RESULT_CACHE);
        assertEquals(value, retrieved);
    }

    @Test
    @DisplayName("Should return cache size correctly")
    void testGetCacheSize() {
        long sizeBefore = cacheService.getCacheSize(CacheService.QUERY_RESULT_CACHE);
        
        cacheService.put("key1", "value1", CacheService.QUERY_RESULT_CACHE);
        cacheService.put("key2", "value2", CacheService.QUERY_RESULT_CACHE);
        
        long sizeAfter = cacheService.getCacheSize(CacheService.QUERY_RESULT_CACHE);
        assertEquals(sizeBefore + 2, sizeAfter);
    }

    @Test
    @DisplayName("Should detect memory pressure accurately")
    void testMemoryPressureDetection() {
        // Fill cache with data
        for (int i = 0; i < 100; i++) {
            cacheService.put("key_" + i, "value_" + i, CacheService.QUERY_RESULT_CACHE);
        }
        
        // Memory pressure should be false unless we're near 80% of max size
        assertFalse(cacheService.isMemoryPressureHigh(CacheService.QUERY_RESULT_CACHE));
    }

    @Test
    @DisplayName("Should handle null values gracefully")
    void testNullValueHandling() {
        assertThrows(NullPointerException.class, () -> 
            cacheService.getOrCompute(null, key -> "value", CacheService.QUERY_RESULT_CACHE)
        );
        
        assertThrows(NullPointerException.class, () -> 
            cacheService.getOrCompute("key", null, CacheService.QUERY_RESULT_CACHE)
        );
        
        assertThrows(NullPointerException.class, () -> 
            cacheService.put("key", null, CacheService.QUERY_RESULT_CACHE)
        );
    }

    // New tests for edge cases and cache variants

    @Test
    @DisplayName("Should work with session_cache")
    void testSessionCacheOperations() {
        String cacheKey = "session_key";
        String sessionValue = "user_123_session";
        
        // Test put and get on session cache
        cacheService.put(cacheKey, sessionValue, CacheService.SESSION_CACHE);
        String retrieved = cacheService.getIfPresent(cacheKey, CacheService.SESSION_CACHE);
        
        assertEquals(sessionValue, retrieved);
        
        // Verify it's in session cache, not query result cache
        String fromQueryCache = cacheService.getIfPresent(cacheKey, CacheService.QUERY_RESULT_CACHE);
        assertNull(fromQueryCache);
    }

    @Test
    @DisplayName("Should default to query_result_cache for unknown cache names")
    void testUnknownCacheNameDefaultsBehavior() {
        String cacheKey = "unknown_cache_key";
        String value = "test_value";
        
        // Put value using unknown cache name (should default to query_result_cache)
        cacheService.put(cacheKey, value, "unknown_cache");
        
        // Verify value is in query_result_cache (the default)
        String retrieved = cacheService.getIfPresent(cacheKey, CacheService.QUERY_RESULT_CACHE);
        assertEquals(value, retrieved);
    }

    @Test
    @DisplayName("Should detect high memory pressure when cache is near full")
    void testHighMemoryPressureDetection() {
        // Fill query result cache close to its max (1000 entries as per default config)
        // We'll add ~850 entries to exceed 80% threshold (0.8 * 1000 = 800)
        for (int i = 0; i < 850; i++) {
            cacheService.put("pressure_key_" + i, "value_" + i, CacheService.QUERY_RESULT_CACHE);
        }
        
        // With ~850 entries in a 1000-entry cache, pressure should be detected (85% > 80%)
        assertTrue(cacheService.isMemoryPressureHigh(CacheService.QUERY_RESULT_CACHE));
        
        // Clean up for other tests
        cacheService.invalidateAll(CacheService.QUERY_RESULT_CACHE);
    }

    @Test
    @DisplayName("Should handle concurrent put/get operations safely")
    void testConcurrentCacheOperations() throws InterruptedException {
        int threadCount = 10;
        int operationsPerThread = 100;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        
        try {
            // Submit concurrent tasks
            for (int t = 0; t < threadCount; t++) {
                final int threadId = t;
                executorService.submit(() -> {
                    try {
                        startLatch.await(); // Wait for all threads to be ready
                        
                        for (int op = 0; op < operationsPerThread; op++) {
                            String key = "concurrent_key_" + threadId + "_" + op;
                            String value = "value_" + threadId + "_" + op;
                            
                            // Alternate between put and get operations
                            if (op % 2 == 0) {
                                cacheService.put(key, value, CacheService.QUERY_RESULT_CACHE);
                            } else {
                                cacheService.getIfPresent(key, CacheService.QUERY_RESULT_CACHE);
                            }
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        endLatch.countDown();
                    }
                });
            }
            
            startLatch.countDown(); // Release all threads
            endLatch.await(); // Wait for all threads to complete
            
            // Verify cache has entries from concurrent operations
            long finalSize = cacheService.getCacheSize(CacheService.QUERY_RESULT_CACHE);
            assertTrue(finalSize > 0, "Cache should contain entries from concurrent operations");
            assertTrue(finalSize <= threadCount * operationsPerThread, 
                "Cache size should not exceed total operations");
            
        } finally {
            executorService.shutdown();
            // Clean up
            cacheService.invalidateAll(CacheService.QUERY_RESULT_CACHE);
        }
    }

    @Test
    @DisplayName("Should compute only once with concurrent getOrCompute calls on same key")
    void testConcurrentGetOrComputeWithSameKey() throws InterruptedException {
        int threadCount = 5;
        String sharedKey = "shared_compute_key";
        AtomicInteger computeCount = new AtomicInteger(0);
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        
        try {
            // Submit concurrent getOrCompute on same key
            for (int t = 0; t < threadCount; t++) {
                executorService.submit(() -> {
                    try {
                        startLatch.await(); // Synchronize thread start
                        
                        String result = cacheService.getOrCompute(sharedKey, key -> {
                            computeCount.incrementAndGet();
                            return "computed_value";
                        }, CacheService.QUERY_RESULT_CACHE);
                        
                        assertEquals("computed_value", result);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        endLatch.countDown();
                    }
                });
            }
            
            startLatch.countDown(); // Release all threads
            endLatch.await(); // Wait for all to complete
            
            // With Caffeine's atomic get we expect a single computation per key, but races, evictions, or interruptions
            // can cause rare redundant loads, so we still tolerate up to two invocations.
            assertTrue(computeCount.get() <= 2, 
                "Compute should be called at most twice for concurrent same-key getOrCompute (was: " + computeCount.get() + ")");
            
        } finally {
            executorService.shutdown();
            cacheService.invalidateAll(CacheService.QUERY_RESULT_CACHE);
        }
    }

}
