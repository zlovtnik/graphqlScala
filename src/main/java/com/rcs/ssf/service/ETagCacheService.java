package com.rcs.ssf.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Base64;

/**
 * Service for caching ETags in Redis to avoid repeated SHA-256 computation.
 * 
 * ETags are expensive to compute (SHA-256 hashing) and often requested multiple times
 * for the same resource. This service caches them in Redis with a 5-minute TTL,
 * significantly reducing CPU overhead during high traffic.
 * 
 * Cache key format: etag:{base64_hash_of_response_body}
 * Cache TTL: 5 minutes (configurable via app.etag.cache-ttl-minutes)
 */
@Service
@Slf4j
public class ETagCacheService {

    private static final String ETAG_CACHE_PREFIX = "etag:";
    private static final Duration DEFAULT_TTL = Duration.ofMinutes(5);

    private final RedisTemplate<String, String> redisTemplate;
    private final Duration ttl;

    private static final long DEFAULT_TTL_MINUTES = 5;
    
    public ETagCacheService(
            RedisTemplate<String, String> redisTemplate,
            @Value("${app.etag.cache-ttl-minutes:5}") long ttlMinutes) {
        this.redisTemplate = redisTemplate;
        this.ttl = Duration.ofMinutes(ttlMinutes);
    }

    /**
     * Get or compute ETag for response content.
     * Checks Redis cache first; if miss, computes and caches the ETag.
     * 
     * @param content the response body bytes
     * @return the ETag string in format "hash"
     */
    public String getOrComputeETag(byte[] content) {
        String cacheKey = generateCacheKey(content);
        
        // Try to get from Redis cache
        try {
            String cachedETag = redisTemplate.opsForValue().get(cacheKey);
            if (cachedETag != null) {
                log.debug("ETag cache hit for: {}", cacheKey);
                return cachedETag;
            }
        } catch (Exception e) {
            log.warn("Failed to retrieve ETag from Redis cache, falling back to direct computation", e);
        }

        // Cache miss or Redis unavailable - compute ETag
        String etag = computeETag(content);

        // Store in Redis for future requests
        try {
            redisTemplate.opsForValue().set(cacheKey, etag, ttl);
            log.debug("ETag cached in Redis for: {} (TTL: {} minutes)", cacheKey, ttl.toMinutes());
        } catch (Exception e) {
            log.warn("Failed to cache ETag in Redis, continuing without cache", e);
            // Don't fail the request if Redis is unavailable
        }

        return etag;
    }

    /**
     * Compute ETag from response content using SHA-256 hash.
     * 
     * @param content the response body bytes
     * @return the ETag string in format "hash"
     */
    public String computeETag(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content);
            String etag = Base64.getEncoder().encodeToString(hash);
            log.debug("Computed SHA-256 ETag for content (length: {})", content.length);
            return "\"" + etag + "\"";
        } catch (NoSuchAlgorithmException e) {
            log.warn("SHA-256 not available, falling back to content array hashCode", e);
            // Use hashCode of content array as fallback (better than length alone)
            int hashCode = java.util.Arrays.hashCode(content);
            return "\"" + Integer.toHexString(hashCode) + "\"";
        }
    }

    /**
     * Generate Redis cache key from response content.
     * Uses SHA-1 hash of content as key to keep key size reasonable.
     * 
     * @param content the response body bytes
     * @return cache key in format "etag:{hash}"
     */
    private String generateCacheKey(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] hash = digest.digest(content);
            String hashedKey = Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
            return ETAG_CACHE_PREFIX + hashedKey;
        } catch (NoSuchAlgorithmException e) {
            log.error("SHA-1 not available for cache key generation", e);
            return ETAG_CACHE_PREFIX + Integer.toHexString(java.util.Arrays.hashCode(content));
        }
    }

    /**
     * Clear ETag cache (useful for cache invalidation if needed).
     * 
     * @param content the response body bytes
     */
    public void invalidateETag(byte[] content) {
        String cacheKey = generateCacheKey(content);
        try {
            redisTemplate.delete(cacheKey);
            log.debug("ETag cache invalidated for: {}", cacheKey);
        } catch (Exception e) {
            log.warn("Failed to invalidate ETag from Redis cache", e);
        }
    }

    /**
     * Get cache statistics (for monitoring).
     * Uses RedisTemplate.execute() to safely manage connection lifecycle.
     * 
     * @return statistics object with Redis connectivity status
     */
    public ETagCacheStats getStats() {
        try {
            // Use RedisTemplate.execute() to safely manage the connection
            // RedisTemplate handles connection acquisition and release
            redisTemplate.execute((RedisCallback<Boolean>) connection -> {
                connection.ping();
                return true;
            });
            return new ETagCacheStats(true, ttl.toMinutes(), "Redis ETag cache is operational");
        } catch (Exception e) {
            return new ETagCacheStats(false, ttl.toMinutes(), 
                    "Redis ETag cache unavailable: " + e.getMessage());
        }
    }

    /**
     * Statistics object for ETag cache health monitoring.
     */
    public record ETagCacheStats(boolean operational, long ttlMinutes, String status) {}
}
