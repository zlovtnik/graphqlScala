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
 * Applies caching with custom TTL support and secure cache key generation.*
 * Cache Key Strategy:
 * - Combines declaring type, method name, and JSON-serialized arguments
 * - Hashed with SHA-256 for stability and security
 * - Prevents data leakage through cache key inspection
 */
@Aspect
@Component
@Slf4j
@RequiredArgsConstructor
public class GraphqlCachingAspect {

    private final CacheService cacheService;
    private final ObjectMapper objectMapper;

    @Pointcut("@annotation(graphqlCacheable)")
    public void graphqlCacheableMethod(GraphqlCacheable graphqlCacheable) {}

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
        } catch (Exception ex) {
            log.error("Method execution failed for cacheKey: {} in cache: {} - {}", cacheKey, cacheName, ex.getMessage(), ex);
            throw ex;
        }
        
        // Cache the result with custom TTL after validation
        if (result != null) {
            if (ttlSeconds < -1) {
                log.warn("Invalid TTL {} for method: {} - skipping cache", ttlSeconds, joinPoint.getSignature().getName());
                // Skip caching for invalid TTL (only ttlSeconds < -1 is invalid)
            } else {
                // Allow ttlSeconds == 0 (use default), ttlSeconds == -1 (no expiration), or ttlSeconds > 0 (custom TTL)
                cacheService.putWithTtl(cacheKey, result, cacheName, ttlSeconds);
            }
        }
        
        return result;
    }

    private String generateCacheKey(ProceedingJoinPoint joinPoint) {
        try {
            // Build deterministic key payload: type.method(arg1, arg2, ...)
            StringBuilder keyPayload = new StringBuilder();
            keyPayload.append(joinPoint.getSignature().getDeclaringTypeName())
                   .append(".")
                   .append(joinPoint.getSignature().getName())
                   .append("(");
            
            Object[] args = joinPoint.getArgs();
            for (int i = 0; i < args.length; i++) {
                if (i > 0) keyPayload.append(",");
                if (args[i] == null) {
                    keyPayload.append("<null>");
                } else {
                    try {
                        // Use JSON serialization for stable string representation
                        keyPayload.append(objectMapper.writeValueAsString(args[i]));
                    } catch (Exception e) {
                        // Fallback to a content-based stable representation
                        Object arg = args[i];
                        try {
                            if (arg.getClass().isArray()) {
                                // handle common primitive arrays and object arrays
                                if (arg instanceof Object[]) {
                                    keyPayload.append(Arrays.deepHashCode((Object[]) arg));
                                } else if (arg instanceof int[]) {
                                    keyPayload.append(Arrays.hashCode((int[]) arg));
                                } else if (arg instanceof long[]) {
                                    keyPayload.append(Arrays.hashCode((long[]) arg));
                                } else if (arg instanceof byte[]) {
                                    keyPayload.append(Arrays.hashCode((byte[]) arg));
                                } else if (arg instanceof short[]) {
                                    keyPayload.append(Arrays.hashCode((short[]) arg));
                                } else if (arg instanceof char[]) {
                                    keyPayload.append(Arrays.hashCode((char[]) arg));
                                } else if (arg instanceof float[]) {
                                    keyPayload.append(Arrays.hashCode((float[]) arg));
                                } else if (arg instanceof double[]) {
                                    keyPayload.append(Arrays.hashCode((double[]) arg));
                                } else if (arg instanceof boolean[]) {
                                    keyPayload.append(Arrays.hashCode((boolean[]) arg));
                                } else {
                                    // generic fallback for any unknown array type
                                    keyPayload.append(Arrays.deepHashCode(new Object[]{arg}));
                                }
                            } else {
                                // Try standard hashCode (relying on proper equals/hashCode implementations)
                                keyPayload.append(arg.hashCode());
                            }
                        } catch (Exception e2) {
                            // Fallback to stable content-based representation
                            try {
                                if (arg.getClass().isArray()) {
                                    // handle common primitive arrays and object arrays with stable hashing
                                    if (arg instanceof Object[]) {
                                        keyPayload.append(Arrays.deepHashCode((Object[]) arg));
                                    } else if (arg instanceof int[]) {
                                        keyPayload.append(Arrays.hashCode((int[]) arg));
                                    } else if (arg instanceof long[]) {
                                        keyPayload.append(Arrays.hashCode((long[]) arg));
                                    } else if (arg instanceof byte[]) {
                                        keyPayload.append(Arrays.hashCode((byte[]) arg));
                                    } else if (arg instanceof short[]) {
                                        keyPayload.append(Arrays.hashCode((short[]) arg));
                                    } else if (arg instanceof char[]) {
                                        keyPayload.append(Arrays.hashCode((char[]) arg));
                                    } else if (arg instanceof float[]) {
                                        keyPayload.append(Arrays.hashCode((float[]) arg));
                                    } else if (arg instanceof double[]) {
                                        keyPayload.append(Arrays.hashCode((double[]) arg));
                                    } else if (arg instanceof boolean[]) {
                                        keyPayload.append(Arrays.hashCode((boolean[]) arg));
                                    } else {
                                        // generic fallback for any unknown array type
                                        keyPayload.append(Arrays.deepHashCode(new Object[]{arg}));
                                    }
                                } else {
                                    // Use a stable representation based on class and content
                                    // Avoid toString() as it may include non-deterministic data
                                    keyPayload.append(arg.getClass().getName()).append(":").append(arg.hashCode());
                                }
                            } catch (Exception e3) {
                                // Final fallback: use class name only for basic stability
                                keyPayload.append(arg.getClass().getName()).append(":unhashable");
                            }
                        }
                    }
                }
            }
            keyPayload.append(")");
            
            // Hash with SHA-256 for security and stability
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(keyPayload.toString().getBytes());
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            log.error("SHA-256 algorithm not available, using fallback key generation", e);
            // Fallback: use a simpler key if SHA-256 is unavailable
            // Use Arrays.deepHashCode for stable, content-based hash instead of identity hash
            return joinPoint.getSignature().getDeclaringTypeName() + "." + 
                   joinPoint.getSignature().getName() + "." +
                   Arrays.deepHashCode(joinPoint.getArgs());
        }
    }
}