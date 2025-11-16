package com.rcs.ssf.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * Service for managing multi-level caching with invalidation and warm-up strategies.
 * 
 * Cache configuration is read from application.yml (cache.config section) with sensible defaults.
 * 
 * Features:
 * - Query result caching with invalidation strategy (on-write)
 * - Cache statistics and monitoring
 * - Cache warm-up for critical queries
 * - Memory-aware cache operations
 * - Type-safe cache access with optional runtime type validation
 */
@Service
@Slf4j
public class CacheService {

    // Cache name constants to avoid typos and improve maintainability
    public static final String SESSION_CACHE = "session_cache";
    public static final String QUERY_RESULT_CACHE = "query_result_cache";

    private final Cache<String, Object> queryResultCache;
    private final Cache<String, Object> sessionCache;
    private final CacheConfiguration cacheConfiguration;
    
    // Maps to store per-entry TTL expiration times (in nanoseconds)
    // Entry not present = use default cache TTL; Long.MAX_VALUE = no expiration
    private final ConcurrentHashMap<String, Long> queryResultCacheExpirations = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> sessionCacheExpirations = new ConcurrentHashMap<>();

    /**
     * Constructor that initializes caches with configuration from application.yml.
     * 
     * @param cacheConfiguration Spring-injected configuration containing max sizes and TTLs
     */
    public CacheService(CacheConfiguration cacheConfiguration) {
        this.cacheConfiguration = cacheConfiguration;

        // Query result cache: configured via application.yml (cache.config.query-result-cache)
        CacheConfiguration.CacheProperties queryConfig = cacheConfiguration.getQueryResultCache();
        long queryTtlMinutes = queryConfig.getTtlMinutes();
        this.queryResultCache = Caffeine.newBuilder()
            .maximumSize(queryConfig.getMaxSize())
            .expireAfter(new Expiry<String, Object>() {
                @Override
                public long expireAfterCreate(String key, Object value, long now) {
                    Long customExpiration = queryResultCacheExpirations.get(key);
                    if (customExpiration != null && customExpiration == Long.MAX_VALUE) {
                        // No expiration
                        return Long.MAX_VALUE;
                    } else if (customExpiration != null) {
                        // Custom per-entry TTL
                        return customExpiration;
                    }
                    // Use default TTL
                    return TimeUnit.MINUTES.toNanos(queryTtlMinutes);
                }
                
                @Override
                public long expireAfterUpdate(String key, Object value, long now, long currentDurationNanos) {
                    return expireAfterCreate(key, value, now);
                }
                
                @Override
                public long expireAfterRead(String key, Object value, long now, long currentDurationNanos) {
                    return currentDurationNanos;
                }
            })
            .removalListener((key, value, cause) -> {
                // Clean up the expiration map when entry is removed
                queryResultCacheExpirations.remove((String) key);
            })
            .recordStats()
            .build();

        // Session cache: configured via application.yml (cache.config.session-cache)
        CacheConfiguration.CacheProperties sessionConfig = cacheConfiguration.getSessionCache();
        long sessionTtlMinutes = sessionConfig.getTtlMinutes();
        this.sessionCache = Caffeine.newBuilder()
            .maximumSize(sessionConfig.getMaxSize())
            .expireAfter(new Expiry<String, Object>() {
                @Override
                public long expireAfterCreate(String key, Object value, long now) {
                    Long customExpiration = sessionCacheExpirations.get(key);
                    if (customExpiration != null && customExpiration == Long.MAX_VALUE) {
                        // No expiration
                        return Long.MAX_VALUE;
                    } else if (customExpiration != null) {
                        // Custom per-entry TTL
                        return customExpiration;
                    }
                    // Use default TTL
                    return TimeUnit.MINUTES.toNanos(sessionTtlMinutes);
                }
                
                @Override
                public long expireAfterUpdate(String key, Object value, long now, long currentDurationNanos) {
                    return expireAfterCreate(key, value, now);
                }
                
                @Override
                public long expireAfterRead(String key, Object value, long now, long currentDurationNanos) {
                    return currentDurationNanos;
                }
            })
            .removalListener((key, value, cause) -> {
                // Clean up the expiration map when entry is removed
                sessionCacheExpirations.remove((String) key);
            })
            .recordStats()
            .build();

        log.info("CacheService initialized - Query result cache: maxSize={}, ttlMinutes={}; Session cache: maxSize={}, ttlMinutes={}",
            queryConfig.getMaxSize(), queryTtlMinutes,
            sessionConfig.getMaxSize(), sessionTtlMinutes);
    }


    /**
     * Get a cached value or compute and cache it using the provided function.
     * 
     * <p><strong>Type Safety Warning:</strong> This method performs an unchecked cast from the
     * generic cache storage (Object) to the requested type &lt;T&gt;. If the stored value's type
     * does not match the caller's expected type, a {@link ClassCastException} will be thrown at
     * runtime. For type-safe access with runtime validation, use
     * {@link #getOrComputeTypeSafe(String, Function, String, Class)} instead.</p>
     * 
     * @param cacheKey the cache key (must not be null)
     * @param computeFunction function to compute the value if not cached (must not be null)
     * @param cacheName the name of the cache to use (e.g., {@link #QUERY_RESULT_CACHE}, {@link #SESSION_CACHE})
     * @return the cached value or computed value
     * @throws ClassCastException if the cached value is not assignable to type &lt;T&gt;
     * @throws NullPointerException if cacheKey or computeFunction is null
     */
    @SuppressWarnings("unchecked")
    public <T> T getOrCompute(String cacheKey, Function<String, T> computeFunction, String cacheName) {
        Objects.requireNonNull(cacheKey, "Cache key cannot be null");
        Objects.requireNonNull(computeFunction, "Compute function cannot be null");
        Objects.requireNonNull(cacheName, "Cache name cannot be null");

        Cache<String, Object> cache = getCacheByName(cacheName);
        
        return (T) cache.get(cacheKey, key -> {
            log.debug("Cache miss for key: {} in cache: {}", key, cacheName);
            return computeFunction.apply(key);
        });
    }

    /**
     * Get a cached value or compute and cache it using the provided function with runtime type validation.
     * 
     * <p>This variant provides type-safe access by validating that the cached value is assignable
     * to the expected type before returning it. If type validation fails, a more descriptive
     * {@link ClassCastException} is thrown with details about the type mismatch.</p>
     * 
     * @param cacheKey the cache key (must not be null)
     * @param computeFunction function to compute the value if not cached (must not be null)
     * @param cacheName the name of the cache to use (e.g., {@link #QUERY_RESULT_CACHE}, {@link #SESSION_CACHE})
     * @param expectedType the expected type for runtime validation (must not be null)
     * @return the cached value or computed value, guaranteed to be assignable to expectedType
     * @throws ClassCastException if the cached value is not assignable to expectedType
     * @throws NullPointerException if cacheKey, computeFunction, or expectedType is null
     */
    public <T> T getOrComputeTypeSafe(String cacheKey, Function<String, T> computeFunction, String cacheName, Class<T> expectedType) {
        Objects.requireNonNull(cacheKey, "Cache key cannot be null");
        Objects.requireNonNull(computeFunction, "Compute function cannot be null");
        Objects.requireNonNull(expectedType, "Expected type cannot be null");
        Objects.requireNonNull(cacheName, "Cache name cannot be null");

        Cache<String, Object> cache = getCacheByName(cacheName);
        
        Object cachedValue = cache.get(cacheKey, key -> {
            log.debug("Cache miss for key: {} in cache: {}", key, cacheName);
            return computeFunction.apply(key);
        });

        if (!expectedType.isInstance(cachedValue)) {
            throw new ClassCastException(String.format(
                "Cached value for key '%s' is of type %s, but expected type %s",
                cacheKey, cachedValue.getClass().getName(), expectedType.getName()
            ));
        }

        return expectedType.cast(cachedValue);
    }


    /**
     * Get a value from cache if present.
     * 
     * <p><strong>Type Safety Warning:</strong> This method performs an unchecked cast from the
     * generic cache storage (Object) to the requested type &lt;T&gt;. If the stored value's type
     * does not match the caller's expected type, a {@link ClassCastException} will be thrown at
     * runtime. For type-safe access with runtime validation, use
     * {@link #getIfPresentTypeSafe(String, String, Class)} instead.</p>
     * 
     * @param cacheKey the cache key to retrieve
     * @param cacheName the name of the cache to use (e.g., {@link #QUERY_RESULT_CACHE}, {@link #SESSION_CACHE})
     * @return the cached value if present, or null if absent
     * @throws ClassCastException if the cached value is not assignable to type &lt;T&gt;
     */
    @SuppressWarnings("unchecked")
    public <T> T getIfPresent(String cacheKey, String cacheName) {
        Objects.requireNonNull(cacheName, "Cache name cannot be null");
        Cache<String, Object> cache = getCacheByName(cacheName);
        return (T) cache.getIfPresent(cacheKey);
    }

    /**
     * Get a value from cache if present with runtime type validation.
     * 
     * <p>This variant provides type-safe access by validating that the cached value is assignable
     * to the expected type before returning it. If type validation fails, a more descriptive
     * {@link ClassCastException} is thrown with details about the type mismatch.</p>
     * 
     * @param cacheKey the cache key to retrieve
     * @param cacheName the name of the cache to use (e.g., {@link #QUERY_RESULT_CACHE}, {@link #SESSION_CACHE})
     * @param expectedType the expected type for runtime validation (must not be null)
     * @return the cached value if present and of correct type, or null if absent or type mismatch
     * @throws NullPointerException if expectedType is null
     */
    public <T> T getIfPresentTypeSafe(String cacheKey, String cacheName, Class<T> expectedType) {
        Objects.requireNonNull(expectedType, "Expected type cannot be null");
        Objects.requireNonNull(cacheName, "Cache name cannot be null");
        
        Cache<String, Object> cache = getCacheByName(cacheName);
        Object cachedValue = cache.getIfPresent(cacheKey);
        
        if (cachedValue == null) {
            return null;
        }

        if (!expectedType.isInstance(cachedValue)) {
            log.warn("Type mismatch for cache key '{}' in cache '{}': stored={}, expected={}",
                cacheKey, cacheName, cachedValue.getClass().getName(), expectedType.getName());
            return null;
        }

        return expectedType.cast(cachedValue);
    }


    /**
     * Put a value into cache explicitly.
     */
    public void put(String cacheKey, Object value, String cacheName) {
        putWithTtl(cacheKey, value, cacheName, 0);
    }

    /**
     * Put a value into cache with custom TTL.
     * 
     * @param cacheKey the cache key
     * @param value the value to cache
     * @param cacheName the cache name
     * @param ttlSeconds TTL in seconds: 0 = use default, -1 = no expiration, >0 = custom TTL
     */
    public void putWithTtl(String cacheKey, Object value, String cacheName, long ttlSeconds) {
        Objects.requireNonNull(cacheKey, "Cache key cannot be null");
        Objects.requireNonNull(value, "Value cannot be null");
        Objects.requireNonNull(cacheName, "Cache name cannot be null");

        Cache<String, Object> cache = getCacheByName(cacheName);
        ConcurrentHashMap<String, Long> expirationMap = getExpirationMapForCache(cacheName);
        
        if (ttlSeconds == 0) {
            // Use default TTL - remove from expiration map if present
            expirationMap.remove(cacheKey);
            cache.put(cacheKey, value);
        } else if (ttlSeconds == -1) {
            // No expiration - store sentinel value
            expirationMap.put(cacheKey, Long.MAX_VALUE);
            cache.put(cacheKey, value);
        } else if (ttlSeconds > 0) {
            // Custom TTL - store expiration time in nanoseconds
            long expirationNanos = TimeUnit.SECONDS.toNanos(ttlSeconds);
            expirationMap.put(cacheKey, expirationNanos);
            cache.put(cacheKey, value);
        } else {
            // Invalid TTL - log warning and use default
            log.warn("Invalid ttlSeconds: {} for key: {} - using default TTL", ttlSeconds, cacheKey);
            expirationMap.remove(cacheKey);
            cache.put(cacheKey, value);
        }
        
        log.debug("Cached value for key: {} in cache: {} with ttlSeconds: {}", cacheKey, cacheName, ttlSeconds);
    }

    /**
     * Get the expiration map for a given cache name.
     * 
     * @param cacheName the cache name
     * @return the expiration map for the cache
     */
    private ConcurrentHashMap<String, Long> getExpirationMapForCache(String cacheName) {
        if (QUERY_RESULT_CACHE.equals(cacheName)) {
            return queryResultCacheExpirations;
        } else if (SESSION_CACHE.equals(cacheName)) {
            return sessionCacheExpirations;
        } else {
            throw new IllegalArgumentException("Unknown cache name: " + cacheName);
        }
    }

    /**
     * Invalidate a specific cache key.
     */
    public void invalidate(String cacheKey, String cacheName) {
        Objects.requireNonNull(cacheName, "Cache name cannot be null");
        Cache<String, Object> cache = getCacheByName(cacheName);
        cache.invalidate(cacheKey);
        log.info("Invalidated cache key: {} from cache: {}", cacheKey, cacheName);
    }

    /**
     * Invalidate all entries in a cache (on-write strategy).
     */
    public void invalidateAll(String cacheName) {
        Objects.requireNonNull(cacheName, "Cache name cannot be null");
        Cache<String, Object> cache = getCacheByName(cacheName);
        cache.invalidateAll();
        log.info("Invalidated all entries in cache: {}", cacheName);
    }

    /**
     * Warm up cache with critical query results.
     * Should be called during application startup.
     */
    public void warmUpCache(String cacheKey, Object value, String cacheName) {
        Objects.requireNonNull(cacheName, "Cache name cannot be null");
        Objects.requireNonNull(value, "Value cannot be null");
        Cache<String, Object> cache = getCacheByName(cacheName);
        cache.put(cacheKey, value);
        log.info("Warmed up cache key: {} in cache: {}", cacheKey, cacheName);
    }

    /**
     * Get cache size for monitoring memory usage.
     */
    public long getCacheSize(String cacheName) {
        Objects.requireNonNull(cacheName, "Cache name cannot be null");
        Cache<String, Object> cache = getCacheByName(cacheName);
        return cache.estimatedSize();
    }

    /**
     * Check memory pressure - return true if cache is near capacity.
     * 
    * <p>Memory pressure is considered high when the cache size exceeds the configured
    * threshold fraction of the configured maximum size for that cache. The thresholds and
    * fraction are derived from application.yml configuration (cache.config.{cache-name}.max-size
    * and cache.config.memory-pressure-threshold).</p>
     * 
     * @param cacheName the name of the cache to check (e.g., {@link #QUERY_RESULT_CACHE}, {@link #SESSION_CACHE})
     * @return true if cache size exceeds the configured memory-pressure threshold fraction; false otherwise
     */
    public boolean isMemoryPressureHigh(String cacheName) {
        Objects.requireNonNull(cacheName, "Cache name cannot be null");
        long size = getCacheSize(cacheName);
        long maxSize = getConfiguredMaxSize(cacheName);
        double thresholdFraction = cacheConfiguration.getMemoryPressureThreshold();
        long threshold = (long) (maxSize * thresholdFraction);
        return size > threshold;
    }

    /**
     * Get the configured maximum size for a given cache.
     * 
     * @param cacheName the name of the cache (e.g., {@link #QUERY_RESULT_CACHE}, {@link #SESSION_CACHE})
     * @return the configured maximum size from application.yml
     */
    private long getConfiguredMaxSize(String cacheName) {
        if (QUERY_RESULT_CACHE.equals(cacheName)) {
            return cacheConfiguration.getQueryResultCache().getMaxSize();
        } else if (SESSION_CACHE.equals(cacheName)) {
            return cacheConfiguration.getSessionCache().getMaxSize();
        } else {
            // Default fallback for unknown cache names
            log.warn("Unknown cache name: {}, using default max size of 1000", cacheName);
            return 1000;
        }
    }


    /**
     * Helper method to get the appropriate cache instance.
     * 
     * @param cacheName the name of the cache (use constants {@link #QUERY_RESULT_CACHE} or {@link #SESSION_CACHE})
     * @return the Cache instance for the given name
     */
    private Cache<String, Object> getCacheByName(String cacheName) {
        return switch (cacheName) {
            case SESSION_CACHE -> sessionCache;
            case QUERY_RESULT_CACHE -> queryResultCache;
            default -> throw new IllegalArgumentException("Unknown cache name: " + cacheName);
        };
    }


}
