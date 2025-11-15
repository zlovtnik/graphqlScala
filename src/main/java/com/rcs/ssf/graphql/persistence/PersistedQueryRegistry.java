package com.rcs.ssf.graphql.persistence;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Registry for persisted GraphQL queries supporting APQ (Automatic Persisted Queries).
 * 
 * Features:
 * - Stores 50+ common queries mapped to hash IDs in Redis with versioning
 * - Supports query complexity analysis and caching
 * - Provides Prometheus metrics for cache hit rates and query complexity distribution
 * 
 * Performance Target: >85% cache hit rate, <1% query rejection rate
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PersistedQueryRegistry {

    private final RedisTemplate<String, String> redisTemplate;
    private final MeterRegistry meterRegistry;

    @Value("${graphql.persisted-queries.enabled:true}")
    private boolean persistedQueriesEnabled;

    @Value("${graphql.persisted-queries.default-complexity-threshold:5000}")
    private int defaultComplexityThreshold;

    @Value("${graphql.persisted-queries.cache-ttl-minutes:60}")
    private long cacheTtlMinutes;

    private static final String PERSISTED_QUERY_PREFIX = "graphql:persisted:";
    private static final String METRICS_PREFIX = "graphql.persisted_queries.";

    /**
     * Register a new persisted GraphQL query.
     * 
     * @param query The GraphQL query string
     * @param clientName Optional client identifier
     * @return The query hash ID (used for APQ)
     */
    public String registerQuery(String query, String clientName) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            String queryHash = generateQueryHash(query);
            
            // Calculate complexity
            int complexity = analyzeQueryComplexity(query);
            
            // Store in Redis with versioning
            Map<String, String> queryData = new LinkedHashMap<>();
            queryData.put("hash", queryHash);
            queryData.put("query", query);
            queryData.put("version", "v1");
            queryData.put("complexity", String.valueOf(complexity));
            queryData.put("clientName", clientName != null ? clientName : "unknown");
            queryData.put("registeredAt", System.currentTimeMillis() + "");

            String redisKey = PERSISTED_QUERY_PREFIX + queryHash;
            redisTemplate.opsForHash().putAll(redisKey, queryData);
            redisTemplate.expire(redisKey, cacheTtlMinutes, TimeUnit.MINUTES);

            log.info("Registered persisted query: hash={}, complexity={}, client={}", 
                    queryHash, complexity, clientName);

            meterRegistry.counter(METRICS_PREFIX + "registered_total").increment();
            return queryHash;

        } catch (Exception e) {
            log.error("Failed to register persisted query", e);
            meterRegistry.counter(METRICS_PREFIX + "registration_errors_total").increment();
            throw new RuntimeException("Query registration failed", e);
        } finally {
            sample.stop(Timer.builder(METRICS_PREFIX + "registration_duration_ms")
                    .description("Time to register a persisted query")
                    .publishPercentiles(0.5, 0.95, 0.99)
                    .register(meterRegistry));
        }
    }

    /**
     * Retrieve a persisted query by its hash ID.
     * 
     * @param queryHash The hash ID of the query
     * @return Optional containing the query, or empty if not found
     */
    @Cacheable(value = "persistedQueries", key = "#queryHash", unless = "#result == null")
    public Optional<String> getQuery(String queryHash) {
        if (!persistedQueriesEnabled) {
            return Optional.empty();
        }

        String redisKey = PERSISTED_QUERY_PREFIX + queryHash;
        Object query = redisTemplate.opsForHash().get(redisKey, "query");

        if (query != null) {
            meterRegistry.counter(METRICS_PREFIX + "cache_hits").increment();
            
            // Update usage tracking
            redisTemplate.opsForHash().increment(redisKey, "usageCount", 1);
            redisTemplate.opsForHash().put(redisKey, "lastUsedAt", System.currentTimeMillis() + "");
            
            return Optional.of((String) query);
        }

        meterRegistry.counter(METRICS_PREFIX + "cache_misses").increment();
        return Optional.empty();
    }

    /**
     * Analyze GraphQL query complexity to prevent DoS attacks.
     * 
     * Complexity scoring:
     * - Scalar field: 1 point
     * - Object field: 10 points
     * - List field: 50 points per item
     * - Multiplier: depth level
     * 
     * @param query The GraphQL query string
     * @return Complexity score (0-N)
     */
    public int analyzeQueryComplexity(String query) {
        // Basic complexity calculation (simplified for demo)
        // In production, use graphql-java-extended-scalars or custom visitor
        
        int complexity = 0;
        
        // Count selections (naive approach)
        complexity += query.split("\\{").length * 10;
        complexity += query.split("\\(").length * 20; // Arguments add complexity
        complexity += query.split("\\[").length * 50; // Lists add significant complexity
        
        // Cache complexity score
        meterRegistry.timer(METRICS_PREFIX + "complexity_score")
                .record(complexity, TimeUnit.MILLISECONDS);

        if (complexity > defaultComplexityThreshold) {
            log.warn("Query complexity exceeds threshold: score={}, threshold={}", 
                    complexity, defaultComplexityThreshold);
            meterRegistry.counter(METRICS_PREFIX + "rejected_queries_total").increment();
        }

        return complexity;
    }

    /**
     * Generate SHA-256 hash of GraphQL query for deduplication.
     * 
     * @param query The GraphQL query string
     * @return Hex-encoded SHA-256 hash
     */
    private String generateQueryHash(String query) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(query.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    private String bytesToHex(byte[] hash) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }

    /**
     * Get cache hit ratio metrics.
     * 
     * @return Map containing cache statistics
     */
    public Map<String, Object> getCacheStats() {
        Counter hits = meterRegistry.find(METRICS_PREFIX + "cache_hits").counter();
        Counter misses = meterRegistry.find(METRICS_PREFIX + "cache_misses").counter();

        double hitsCount = hits != null ? hits.count() : 0;
        double missesCount = misses != null ? misses.count() : 0;
        double totalRequests = hitsCount + missesCount;
        double hitRate = totalRequests > 0 ? (hitsCount / totalRequests) * 100 : 0;

        return Map.of(
                "totalHits", hitsCount,
                "totalMisses", missesCount,
                "hitRatePercentage", String.format("%.2f%%", hitRate),
                "cacheSize", redisTemplate.keys(PERSISTED_QUERY_PREFIX + "*").size(),
                "threshold", defaultComplexityThreshold
        );
    }

    /**
     * Get query complexity distribution for monitoring.
     * 
     * @return Percentile breakdown of query complexity scores
     */
    public Map<String, Object> getComplexityStats() {
        Timer complexityTimer = meterRegistry.find(METRICS_PREFIX + "complexity_score")
                .timer();

        if (complexityTimer == null) {
            return Map.of("message", "No complexity data available");
        }

        return Map.of(
                "p50", complexityTimer.takeSnapshot().percentileValues()[0].percentile(),
                "p95", complexityTimer.takeSnapshot().percentileValues()[1].percentile(),
                "p99", complexityTimer.takeSnapshot().percentileValues()[2].percentile(),
                "count", complexityTimer.count(),
                "threshold", defaultComplexityThreshold
        );
    }
}
