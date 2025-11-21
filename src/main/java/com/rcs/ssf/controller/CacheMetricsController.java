package com.rcs.ssf.controller;

import com.github.benmanes.caffeine.cache.Cache;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.cache.CacheManager;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * Exposes cache metrics and statistics for monitoring cache performance.
 * Provides direct access to Caffeine cache stats and Micrometer metrics.
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

        for (String cacheName : cacheManager.getCacheNames()) {
            org.springframework.cache.Cache cache = cacheManager.getCache(cacheName);
            if (cache != null) {
                Object nativeCache = cache.getNativeCache();
                if (nativeCache instanceof Cache) {
                    @SuppressWarnings("unchecked")
                    Cache<Object, Object> caffeineCache = (Cache<Object, Object>) nativeCache;

                    Map<String, Object> cacheStats = new HashMap<>();
                    cacheStats.put("stats", caffeineCache.stats().toString());
                    cacheStats.put("estimatedSize", caffeineCache.estimatedSize());
                    
                    // Parse stats into individual metrics
                    var stats = caffeineCache.stats();
                    cacheStats.put("hitCount", stats.hitCount());
                    cacheStats.put("missCount", stats.missCount());
                    cacheStats.put("loadSuccessCount", stats.loadSuccessCount());
                    cacheStats.put("loadFailureCount", stats.loadFailureCount());
                    cacheStats.put("evictionCount", stats.evictionCount());
                    cacheStats.put("hitRate", stats.hitRate());

                    allStats.put(cacheName, cacheStats);
                }
            }
        }

        return allStats;
    }

    /**
     * Returns Micrometer cache metrics (requires metrics to be enabled in actuator).
     */
    @GetMapping("/micrometer")
    public Map<String, Object> getMicrometerMetrics() {
        Map<String, Object> metrics = new HashMap<>();

        // Cache hits
        var cacheHits = meterRegistry.find("cache.hits")
                .gauges()
                .stream()
                .map(g -> Map.of(
                        "name", g.getId().getName(),
                        "value", g.value(),
                        "tags", g.getId().getTags().stream()
                                .collect(java.util.stream.Collectors.toMap(
                                        t -> t.getKey(),
                                        t -> t.getValue()
                                ))
                ))
                .toList();

        metrics.put("cache.hits", cacheHits);

        // Cache misses
        var cacheMisses = meterRegistry.find("cache.misses")
                .gauges()
                .stream()
                .map(g -> Map.of(
                        "name", g.getId().getName(),
                        "value", g.value(),
                        "tags", g.getId().getTags().stream()
                                .collect(java.util.stream.Collectors.toMap(
                                        t -> t.getKey(),
                                        t -> t.getValue()
                                ))
                ))
                .toList();

        metrics.put("cache.misses", cacheMisses);

        // Cache puts
        var cachePuts = meterRegistry.find("cache.puts")
                .gauges()
                .stream()
                .map(g -> Map.of(
                        "name", g.getId().getName(),
                        "value", g.value(),
                        "tags", g.getId().getTags().stream()
                                .collect(java.util.stream.Collectors.toMap(
                                        t -> t.getKey(),
                                        t -> t.getValue()
                                ))
                ))
                .toList();

        metrics.put("cache.puts", cachePuts);

        return metrics;
    }

    /**
     * Returns a summary of all cache statistics.
     */
    @GetMapping("/summary")
    public Map<String, Object> getSummary() {
        Map<String, Object> summary = new HashMap<>();

        Map<String, Object> caffeineStats = new HashMap<>();
        long totalHits = 0;
        long totalMisses = 0;
        long totalEvictions = 0;

        for (String cacheName : cacheManager.getCacheNames()) {
            org.springframework.cache.Cache cache = cacheManager.getCache(cacheName);
            if (cache != null) {
                Object nativeCache = cache.getNativeCache();
                if (nativeCache instanceof Cache) {
                    @SuppressWarnings("unchecked")
                    Cache<Object, Object> caffeineCache = (Cache<Object, Object>) nativeCache;
                    
                    var stats = caffeineCache.stats();
                    totalHits += stats.hitCount();
                    totalMisses += stats.missCount();
                    totalEvictions += stats.evictionCount();

                    caffeineStats.put(cacheName, Map.of(
                            "size", caffeineCache.estimatedSize(),
                            "hits", stats.hitCount(),
                            "misses", stats.missCount(),
                            "hitRate", String.format("%.2f%%", stats.hitRate() * 100),
                            "evictions", stats.evictionCount()
                    ));
                }
            }
        }

        summary.put("caches", caffeineStats);
        summary.put("totalHits", totalHits);
        summary.put("totalMisses", totalMisses);
        summary.put("totalEvictions", totalEvictions);
        summary.put("overallHitRate", totalMisses == 0 ? 
                "N/A (no misses)" : 
                String.format("%.2f%%", (double) totalHits / (totalHits + totalMisses) * 100));

        return summary;
    }
}
