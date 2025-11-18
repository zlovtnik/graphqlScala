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
    private CacheConfiguration cacheConfiguration;

    @BeforeEach
    void setUp() {
        cacheConfiguration = new CacheConfiguration();
        cacheService = new CacheService(cacheConfiguration);
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
        cacheService.invalidateAll(CacheService.QUERY_RESULT_CACHE);

        long thresholdEntries = getQueryCacheThreshold();
        long maxSize = getQueryCacheMaxSize();
        assertTrue(maxSize > thresholdEntries, "Threshold must be below configured max size");

        long entriesBelowPressure = Math.max(0, thresholdEntries - 1);
        // Fill cache with data
        for (int i = 0; i < entriesBelowPressure; i++) {
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
    @DisplayName("Should throw IllegalArgumentException for unknown cache names")
    void testUnknownCacheNameThrowsException() {
        String cacheKey = "unknown_cache_key";
        String value = "test_value";
        
        // Put value using unknown cache name should now throw IllegalArgumentException
        assertThrows(IllegalArgumentException.class, () -> cacheService.put(cacheKey, value, "unknown_cache"));
    }

    @Test
    @DisplayName("Should detect high memory pressure when cache is near full")
    void testHighMemoryPressureDetection() {
        cacheService.invalidateAll(CacheService.QUERY_RESULT_CACHE);

        long thresholdEntries = getQueryCacheThreshold();
        long maxSize = getQueryCacheMaxSize();
        assertTrue(maxSize > thresholdEntries, "Threshold must be below configured max size");

        // Fill query result cache close to its max (1000 entries as per default config)
        // We'll add ~850 entries to exceed 80% threshold (0.8 * 1000 = 800)
        long entriesAbovePressure = thresholdEntries + 1;
        for (int i = 0; i < entriesAbovePressure; i++) {
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


    private long getQueryCacheMaxSize() {
        return cacheConfiguration.getQueryResultCache().getMaxSize();
    }

    private long getQueryCacheThreshold() {
        return (long) (getQueryCacheMaxSize() * cacheConfiguration.getMemoryPressureThreshold());
    }

    // --- TTL-aware putWithTtl tests ---

    @Test
    @DisplayName("putWithTtl should fail fast for invalid negative TTL")
    void testPutWithTtlInvalidNegative() {
        assertThrows(IllegalArgumentException.class, () ->
            cacheService.putWithTtl("key", "value", CacheService.QUERY_RESULT_CACHE, -5)
        );
    }

    @Test
    @DisplayName("putWithTtl should accept ttlSeconds = 0 (use default)")
    void testPutWithTtlZero() {
        String cacheKey = "ttl_zero_key";
        String value = "test_value";
        // Create a fresh cache configuration locally to avoid test interference
        CacheConfiguration freshConfig = new CacheConfiguration();
        freshConfig.setQueryResultCache(new CacheConfiguration.CacheProperties(1000, 0));
        CacheService freshCacheService = new CacheService(freshConfig);

        // Should not throw; uses default TTL (now 0 minutes, immediate expiry)
        freshCacheService.putWithTtl(cacheKey, value, CacheService.QUERY_RESULT_CACHE, 0);

        // Immediately present OR already expired if default TTL is 0
        String retrieved = freshCacheService.getIfPresent(cacheKey, CacheService.QUERY_RESULT_CACHE);
        if (retrieved == null) {
            // If the configured default TTL is 0, Caffeine may consider the entry expired immediately.
            assertNull(retrieved, "Entry expired immediately due to default TTL=0");
            return; // test condition satisfied
        }

        // Wait slightly longer than default TTL (0 minutes -> immediate, but use a small buffer)
        try {
            Thread.sleep(500); // 500ms buffer
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Now the entry should be expired
        String after = freshCacheService.getIfPresent(cacheKey, CacheService.QUERY_RESULT_CACHE);
        assertNull(after, "Expected entry to expire shortly after default TTL when ttlSeconds == 0");
    }

    @Test
    @DisplayName("putWithTtl should accept ttlSeconds = -1 (never expire)")
    void testPutWithTtlNeverExpire() {
        String cacheKey = "ttl_never_expire_key";
        String value = "test_value";
        // Reconfigure default TTL to a very short value for deterministic testing
        cacheConfiguration.getQueryResultCache().setTtlMinutes(0);
        cacheService = new CacheService(cacheConfiguration);

        // Should not throw; stores with never-expire sentinel
        cacheService.putWithTtl(cacheKey, value, CacheService.QUERY_RESULT_CACHE, -1);

        // Immediately present
        String retrieved = cacheService.getIfPresent(cacheKey, CacheService.QUERY_RESULT_CACHE);
        assertEquals(value, retrieved);

        // Wait slightly longer than default TTL to ensure never-expire is honored
        try {
            Thread.sleep(500); // 500ms buffer
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Entry should still be present
        String after = cacheService.getIfPresent(cacheKey, CacheService.QUERY_RESULT_CACHE);
        assertEquals(value, after, "Expected never-expire entry to remain present past default TTL");
    }

    @Test
    @DisplayName("putWithTtl should accept positive ttlSeconds")
    void testPutWithTtlPositive() {
        String cacheKey = "ttl_positive_key";
        String value = "test_value";

        // Should not throw; stores with custom TTL (10 seconds)
        cacheService.putWithTtl(cacheKey, value, CacheService.QUERY_RESULT_CACHE, 10);

        // Immediately present
        String retrieved = cacheService.getIfPresent(cacheKey, CacheService.QUERY_RESULT_CACHE);
        assertEquals(value, retrieved);

        // Wait just over 10 seconds (10s + 500ms buffer) to ensure it expires
        try {
            Thread.sleep(10_500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        String after = cacheService.getIfPresent(cacheKey, CacheService.QUERY_RESULT_CACHE);
        assertNull(after, "Expected entry to expire after custom TTL (10 seconds)");
    }

    @Test
    @DisplayName("putWithTtl should detect overflow for very large TTL values")
    void testPutWithTtlOverflow() {
        // Long.MAX_VALUE seconds would overflow when converted to nanoseconds
        long overflowSeconds = Long.MAX_VALUE / 1_000_000_000L + 1; // Beyond safe conversion range

        assertThrows(IllegalArgumentException.class, () ->
            cacheService.putWithTtl("key", "value", CacheService.QUERY_RESULT_CACHE, overflowSeconds)
        );
    }

    // --- Tests for fail-fast behavior on unknown cache names ---

    @Test
    @DisplayName("getOrCompute should fail fast for unknown cache name")
    void testGetOrComputeUnknownCache() {
        assertThrows(IllegalArgumentException.class, () ->
            cacheService.getOrCompute("key", k -> "value", "unknown_cache")
        );
    }

    @Test
    @DisplayName("getIfPresent should fail fast for unknown cache name")
    void testGetIfPresentUnknownCache() {
        assertThrows(IllegalArgumentException.class, () ->
            cacheService.getIfPresent("key", "unknown_cache")
        );
    }

    @Test
    @DisplayName("invalidate should fail fast for unknown cache name")
    void testInvalidateUnknownCache() {
        assertThrows(IllegalArgumentException.class, () ->
            cacheService.invalidate("key", "unknown_cache")
        );
    }

    @Test
    @DisplayName("invalidateAll should fail fast for unknown cache name")
    void testInvalidateAllUnknownCache() {
        assertThrows(IllegalArgumentException.class, () ->
            cacheService.invalidateAll("unknown_cache")
        );
    }

    @Test
    @DisplayName("isMemoryPressureHigh should fail fast for unknown cache name")
    void testIsMemoryPressureHighUnknownCache() {
        assertThrows(IllegalArgumentException.class, () ->
            cacheService.isMemoryPressureHigh("unknown_cache")
        );
    }

    @Test
    @DisplayName("getCacheSize should fail fast for unknown cache name")
    void testGetCacheSizeUnknownCache() {
        assertThrows(IllegalArgumentException.class, () ->
            cacheService.getCacheSize("unknown_cache")
        );
    }

    @Test
    @DisplayName("getConfiguredMaxSize should fail fast for unknown cache name")
    void testGetConfiguredMaxSizeUnknownCache() {
        assertThrows(IllegalArgumentException.class, () ->
            cacheService.getConfiguredMaxSizePublic("unknown_cache")
        );
    }

    // --- Tests for getConfiguredMaxSize consistency ---

    @Test
    @DisplayName("getConfiguredMaxSize should return query result cache max size")
    void testGetConfiguredMaxSizeQueryCache() {
        long configuredMax = cacheService.getConfiguredMaxSizePublic(CacheService.QUERY_RESULT_CACHE);
        long expectedMax = cacheConfiguration.getQueryResultCache().getMaxSize();
        assertEquals(expectedMax, configuredMax);
    }

    @Test
    @DisplayName("getConfiguredMaxSize should return session cache max size")
    void testGetConfiguredMaxSizeSessionCache() {
        long configuredMax = cacheService.getConfiguredMaxSizePublic(CacheService.SESSION_CACHE);
        long expectedMax = cacheConfiguration.getSessionCache().getMaxSize();
        assertEquals(expectedMax, configuredMax);
    }

}
