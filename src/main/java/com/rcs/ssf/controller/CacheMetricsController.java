package com.rcs.ssf.controller;

import com.github.benmanes.caffeine.cache.Cache;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.cache.CacheManager;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Exposes cache metrics and statistics for monitoring cache performance.
 * Provides direct access to Caffeine cache stats and Micrometer metrics.
 * 
 * Security note: These are regular MVC endpoints, not Spring Boot Actuator @Endpoint beans.
 * Ensure SecurityConfig treats /actuator/cache-metrics/** according to your security requirements.
 * In production, these endpoints should be secured to prevent unauthorized access to cache internals.
 */
@RestController
@RequestMapping("/actuator/cache-metrics")
public class CacheMetricsController {

    private final CacheManager cacheManager;
    private final MeterRegistry meterRegistry;

    public CacheMetricsController(CacheManager cacheManager, MeterRegistry meterRegistry) {
        this.cacheManager = cacheManager;
        this.meterRegistry = meterRegistry;
    }

    /**
     * Returns statistics for all Caffeine caches including hits, misses, evictions.
     */
    @GetMapping("/caffeine")
    public Map<String, Object> getCaffeineStats() {
        Map<String, Object> allStats = new HashMap<>();

        for (CacheEntry entry : getCaffeineCacheEntries()) {
            var stats = entry.cache().stats();

            Map<String, Object> cacheStats = new HashMap<>();
            cacheStats.put("stats", stats.toString());
            cacheStats.put("estimatedSize", entry.cache().estimatedSize());
            cacheStats.put("hitCount", stats.hitCount());
            cacheStats.put("missCount", stats.missCount());
            cacheStats.put("loadSuccessCount", stats.loadSuccessCount());
            cacheStats.put("loadFailureCount", stats.loadFailureCount());
            cacheStats.put("evictionCount", stats.evictionCount());
            cacheStats.put("hitRate", stats.hitRate());

            allStats.put(entry.cacheName(), cacheStats);
        }

        return allStats;
    }

    /**
     * Returns Micrometer cache metrics (requires metrics to be enabled in actuator).
     * Uses cache.gets with result tags (hit|miss) and cache.puts counter.
     * Note: Metrics names and tags depend on Micrometer and cache provider configuration.
     * For Caffeine, Spring exposes: cache.gets (with result tag), cache.puts.
     */
    @GetMapping("/micrometer")
    public Map<String, Object> getMicrometerMetrics() {
        Map<String, Object> metrics = new HashMap<>();

        // Extract metrics for cache hits/misses (from cache.gets with result tags) and puts
        metrics.put("cache.gets", extractMicrometerMetrics("cache.gets"));
        metrics.put("cache.puts", extractMicrometerMetrics("cache.puts"));

        return metrics;
    }

    /**
     * Helper method to extract and format Micrometer metrics by name.
     * Queries meters (counters, function counters, and other meter types) instead of just gauges.
     * Includes tag information for filtering and categorization.
     *
     * @param metricName the metric name (e.g., "cache.gets", "cache.puts")
     * @return list of formatted metric maps with tag information
     */
    private List<Map<String, Object>> extractMicrometerMetrics(String metricName) {
        return meterRegistry.find(metricName)
                .meters()
                .stream()
                .map(meter -> {
                    Map<String, Object> meterData = new HashMap<>();
                    meterData.put("name", meter.getId().getName());
                    
                    // Extract numeric value based on meter type
                    if (meter instanceof io.micrometer.core.instrument.Counter) {
                        meterData.put("value", ((io.micrometer.core.instrument.Counter) meter).count());
                    } else if (meter instanceof io.micrometer.core.instrument.Gauge) {
                        meterData.put("value", ((io.micrometer.core.instrument.Gauge) meter).value());
                    } else if (meter instanceof io.micrometer.core.instrument.FunctionCounter) {
                        meterData.put("value", ((io.micrometer.core.instrument.FunctionCounter) meter).count());
                    } else {
                        // For other meter types, try to extract a numeric value
                        meterData.put("value", "N/A");
                    }
                    
                    // Include tags for categorization (e.g., result=hit|miss)
                    Map<String, String> tags = new HashMap<>();
                    meter.getId().getTags().forEach(tag -> tags.put(tag.getKey(), tag.getValue()));
                    meterData.put("tags", tags);
                    
                    return meterData;
                })
                .toList();
    }

    /**
     * Returns a summary of all cache statistics.
     * Hit-rate semantics:
     * - "N/A (no data)" when both totalHits and totalMisses are 0 (cache not yet accessed)
     * - "100.00%" when totalHits > 0 and totalMisses == 0 (perfect cache hit rate)
     * - "%.2f%%" for normal cases with both hits and misses recorded
     */
    @GetMapping("/summary")
    public Map<String, Object> getSummary() {
        Map<String, Object> summary = new HashMap<>();

        Map<String, Object> caffeineStats = new HashMap<>();
        long totalHits = 0;
        long totalMisses = 0;
        long totalEvictions = 0;

        for (CacheEntry entry : getCaffeineCacheEntries()) {
            var stats = entry.cache().stats();
            totalHits += stats.hitCount();
            totalMisses += stats.missCount();
            totalEvictions += stats.evictionCount();

            caffeineStats.put(entry.cacheName(), Map.of(
                    "size", entry.cache().estimatedSize(),
                    "hits", stats.hitCount(),
                    "misses", stats.missCount(),
                    "hitRate", String.format("%.2f%%", stats.hitRate() * 100),
                    "evictions", stats.evictionCount()
            ));
        }

        summary.put("caches", caffeineStats);
        summary.put("totalHits", totalHits);
        summary.put("totalMisses", totalMisses);
        summary.put("totalEvictions", totalEvictions);
        
        // Compute overall hit rate with clear semantics
        String overallHitRate = computeOverallHitRate(totalHits, totalMisses);
        summary.put("overallHitRate", overallHitRate);

        return summary;
    }

    /**
     * Helper record to pair cache name with Caffeine cache instance.
     * Used to reduce duplication across methods that iterate over Caffeine caches.
     */
    private record CacheEntry(String cacheName, Cache<Object, Object> cache) {}

    /**
     * Extract all Caffeine-backed caches from the cache manager.
     * Reuses iteration logic to avoid duplication across methods.
     *
     * @return list of CacheEntry records for all Caffeine caches
     */
    private List<CacheEntry> getCaffeineCacheEntries() {
        List<CacheEntry> entries = new ArrayList<>();
        
        for (String cacheName : cacheManager.getCacheNames()) {
            org.springframework.cache.Cache cache = cacheManager.getCache(cacheName);
            if (cache != null) {
                Object nativeCache = cache.getNativeCache();
                if (nativeCache instanceof Cache) {
                    @SuppressWarnings("unchecked")
                    Cache<Object, Object> caffeineCache = (Cache<Object, Object>) nativeCache;
                    entries.add(new CacheEntry(cacheName, caffeineCache));
                }
            }
        }
        
        return entries;
    }

    /**
     * Compute overall hit rate with clear semantics for edge cases.
     *
     * @param totalHits total cache hits across all caches
     * @param totalMisses total cache misses across all caches
     * @return hit rate as percentage string or N/A if no data
     */
    private String computeOverallHitRate(long totalHits, long totalMisses) {
        if (totalHits == 0 && totalMisses == 0) {
            return "N/A (no data)";  // Cache has not been accessed yet
        }
        if (totalMisses == 0 && totalHits > 0) {
            return "100.00%";  // Perfect hit rate
        }
        return String.format("%.2f%%", (double) totalHits / (totalHits + totalMisses) * 100);
    }
}
