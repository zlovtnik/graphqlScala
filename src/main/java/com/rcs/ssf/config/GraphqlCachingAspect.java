package com.rcs.ssf.config;

import com.rcs.ssf.graphql.annotation.GraphqlCacheable;
import com.rcs.ssf.service.CacheService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Base64;

/**
 * Aspect that handles @GraphqlCacheable annotation on GraphQL data fetchers.
 * Applies caching with custom TTL support and secure cache key generation.
 * 
 * Cache Key Strategy:
 * - Combines declaring type, method name, and JSON-serialized arguments
 * - Hashed with SHA-256 for stability and security
 * - Prevents data leakage through cache key inspection
 * 
 * Non-Serializable Arguments:
 * - Arguments that cannot be JSON-serialized will cause a fail-fast
 * IllegalArgumentException
 * - This ensures cache keys are always stable and deterministic
 * - For non-serializable types, provide a custom keyProvider
 * via @GraphqlCacheable
 * or ensure arguments implement proper serialization/toString() contracts
 * - Arrays are supported via deep hashing; other non-serializable types must be
 * handled
 * by custom key providers to ensure distributed cache compatibility
 */
@Aspect
@Component
@Slf4j
@RequiredArgsConstructor
public class GraphqlCachingAspect {

    private final CacheService cacheService;
    private final ObjectMapper objectMapper;

    @Pointcut("@annotation(graphqlCacheable)")
    public void graphqlCacheableMethod(GraphqlCacheable graphqlCacheable) {
    }

    @Around("graphqlCacheableMethod(graphqlCacheable)")
    public Object handleCaching(ProceedingJoinPoint joinPoint, GraphqlCacheable graphqlCacheable) throws Throwable {
        String cacheName = graphqlCacheable.value();
        long ttlSeconds = graphqlCacheable.ttlSeconds();

        // Generate cache key from method signature and arguments
        String cacheKey = generateCacheKey(joinPoint);

        // Try to get from cache first
        Object cachedValue = cacheService.getIfPresent(cacheKey, cacheName);
        if (cachedValue != null) {
            log.debug("Cache hit for key: {} in cache: {}", cacheKey, cacheName);
            return cachedValue;
        }

        // Cache miss - execute the method
        log.debug("Cache miss for key: {} in cache: {}", cacheKey, cacheName);
        Object result;
        try {
            result = joinPoint.proceed();
        } catch (Throwable t) {
            log.error("Method execution failed for cacheKey: {} in cache: {} - {}", cacheKey, cacheName, t.getMessage(),
                    t);
            throw t;
        }

        // Cache the result with custom TTL after validation
        if (result != null) {
            if (ttlSeconds < -1) {
                log.warn("Invalid TTL {} for method: {} - skipping cache", ttlSeconds,
                        joinPoint.getSignature().getName());
                // Skip caching for invalid TTL (only ttlSeconds < -1 is invalid)
            } else {
                // Allow ttlSeconds == 0 (use default), ttlSeconds == -1 (no expiration), or
                // ttlSeconds > 0 (custom TTL)
                cacheService.putWithTtl(cacheKey, result, cacheName, ttlSeconds);
            }
        }

        return result;
    }

    private String generateCacheKey(ProceedingJoinPoint joinPoint) {
        String keyPayload = buildKeyPayload(joinPoint);
        return hashKeyPayload(keyPayload, joinPoint);
    }

    /**
     * Builds a cache key payload from the method signature and arguments.
     *
     * Arguments are serialized to JSON when possible. Arrays are supported via deep
     * hashing.
     * Non-serializable, non-array arguments will cause a fail-fast
     * IllegalArgumentException
     * to ensure cache keys are always stable and deterministic across JVM restarts
     * and
     * distributed cache scenarios.
     *
     * For non-serializable types that cannot be handled here, provide a custom
     * keyProvider
     * via {@code @GraphqlCacheable(keyProvider = CustomKeyProvider.class)} or
     * ensure
     * arguments implement proper serialization contracts.
     *
     * @param joinPoint the AspectJ join point containing method signature and
     *                  arguments
     * @return cache key payload string that will be hashed to create the final
     *         cache key
     * @throws IllegalArgumentException if a non-serializable, non-array argument is
     *                                  encountered
     */
    private String buildKeyPayload(ProceedingJoinPoint joinPoint) {
        StringBuilder keyPayload = new StringBuilder();
        keyPayload.append(joinPoint.getSignature().getDeclaringTypeName())
                .append(".")
                .append(joinPoint.getSignature().getName())
                .append("(");

        Object[] args = joinPoint.getArgs();
        for (int i = 0; i < args.length; i++) {
            if (i > 0)
                keyPayload.append(",");
            keyPayload.append(serializeArgument(args[i]));
        }
        keyPayload.append(")");
        return keyPayload.toString();
    }

    private String serializeArgument(Object arg) {
        if (arg == null) {
            return "<null>";
        }
        try {
            return objectMapper.writeValueAsString(arg);
        } catch (Exception e) {
            return getFallbackRepresentation(arg);
        }
    }

    private String getFallbackRepresentation(Object arg) {
        // Arrays are supported via deep hashing
        if (arg.getClass().isArray()) {
            try {
                return Integer.toString(computeArrayHash(arg));
            } catch (Exception ex) {
                log.error("Failed to compute hash for array type: {}", arg.getClass().getName(), ex);
                throw new IllegalArgumentException(
                        "Cannot generate stable cache key for array type: " + arg.getClass().getName() +
                                ". Ensure array elements are serializable or provide a custom keyProvider.",
                        ex);
            }
        }

        // Non-serializable, non-array arguments fail fast
        throw new IllegalArgumentException(
                "Argument of type '" + arg.getClass().getName() + "' is not JSON-serializable and not an array. " +
                        "Cache key generation requires either: " +
                        "(1) JSON-serializable arguments, " +
                        "(2) array arguments, or " +
                        "(3) a custom keyProvider via @GraphqlCacheable(keyProvider = CustomKeyProvider.class). " +
                        "Ensure the argument implements proper serialization or provide a custom key provider.");
    }

    private int computeArrayHash(Object arg) {
        if (arg instanceof Object[]) {
            return Arrays.deepHashCode((Object[]) arg);
        } else if (arg instanceof int[]) {
            return Arrays.hashCode((int[]) arg);
        } else if (arg instanceof long[]) {
            return Arrays.hashCode((long[]) arg);
        } else if (arg instanceof byte[]) {
            return Arrays.hashCode((byte[]) arg);
        } else if (arg instanceof short[]) {
            return Arrays.hashCode((short[]) arg);
        } else if (arg instanceof char[]) {
            return Arrays.hashCode((char[]) arg);
        } else if (arg instanceof float[]) {
            return Arrays.hashCode((float[]) arg);
        } else if (arg instanceof double[]) {
            return Arrays.hashCode((double[]) arg);
        } else if (arg instanceof boolean[]) {
            return Arrays.hashCode((boolean[]) arg);
        }
        // generic fallback for unknown array types
        return Arrays.deepHashCode(new Object[] { arg });
    }

    private String hashKeyPayload(String keyPayload, ProceedingJoinPoint joinPoint) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(keyPayload.getBytes());
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            log.error("SHA-256 algorithm not available, using fallback key generation", e);
            return joinPoint.getSignature().getDeclaringTypeName() + "." +
                    joinPoint.getSignature().getName() + "." +
                    Arrays.deepHashCode(joinPoint.getArgs());
        }
    }
}