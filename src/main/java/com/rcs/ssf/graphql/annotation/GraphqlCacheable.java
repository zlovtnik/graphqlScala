package com.rcs.ssf.graphql.annotation;

import java.lang.annotation.*;

/**
 * Annotation for caching GraphQL resolver responses.
 * 
 * Usage:
 * @GraphqlCacheable("userById")
 * public User getUserById(String id) { ... }
 * 
 * Features:
 * - Uses Caffeine (L1) and Redis (L2) caching backends
 * - Automatic key generation based on method parameters
 * - TTL configurable per cache name
 * - Metrics: cache hits/misses exported to Prometheus
 * - Works with both simple and complex types
 * 
 * Supported on:
 * - GraphQL field resolvers
 * - Data fetching methods
 * - Database query methods
 * 
 * DO NOT use on:
 * - Mutations (use @CacheEvict instead)
 * - Authentication/authorization checks
 * - Methods with side effects
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface GraphqlCacheable {
    
    /**
     * Cache name (backend key).
     * Examples: "userById", "projectByName", "teamMembers"
     * 
     * Names are configured in CacheConfig with TTL and backend strategy.
     */
    String value() default "";

    /**
     * SpEL expression for cache key generation.
     * Default: Uses method parameters as key (if single param) or generates composite key.
     * 
     * Example: "#id" (uses 'id' parameter as cache key)
     * Example: "#id + '#' + #status" (composite key)
     */
    String key() default "";

    /**
     * Condition SpEL expression - only cache if condition is true.
     * 
     * Example: "#id != null" (only cache non-null ids)
     * Example: "#result != null and #result.active" (only cache active items)
     */
    String condition() default "";

    /**
     * Whether to use async caching (non-blocking).
     * 
     * Default: false (use sync caching from Caffeine)
     * Set to true for expensive operations to avoid blocking resolver
     */
    boolean async() default false;

    /**
     * Cache TTL in seconds.
     * 
     * 0 = Use default TTL from cache configuration
     * -1 = No expiration
     * >0 = Specific TTL in seconds
     * 
     * Note: TTL is applied at cache configuration level, not here.
     * This is documentation only.
     */
    long ttlSeconds() default 0;
}
