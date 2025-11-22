package com.rcs.ssf.tracing;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * AOP aspect for automatic span instrumentation of cache operations.
 *
 * <p>Interceptors map directly to Spring's caching annotations and emit spans:
 * <ul>
 *   <li>{@code @Cacheable} → {@code cache.get}</li>
 *   <li>{@code @CachePut} → {@code cache.put}</li>
 *   <li>{@code @CacheEvict} → {@code cache.evict}</li>
 * </ul>
 *
 * <p>Captured attributes include cache name, operation, execution duration, and
 * cache hit signal (only set for {@code @Cacheable}, which always represents a
 * cache miss because the method executes). Additional metadata such as result
 * types and eviction settings help correlate cache behavior across Caffeine
 * and Redis backends.
 */
@Aspect
@Component
@Slf4j
public class CacheOperationInstrumentation {

    private final Tracer tracer;

    public CacheOperationInstrumentation(Tracer tracer) {
        this.tracer = tracer;
    }

    @Around("@annotation(cacheable)")
    public Object traceCacheableOperation(ProceedingJoinPoint joinPoint, Cacheable cacheable) throws Throwable {
        String cacheName = resolveCacheName(cacheable.cacheNames(), cacheable.value(), joinPoint);
        return traceCacheOperation(joinPoint, cacheName, "get", false, null);
    }

    @Around("@annotation(cachePut)")
    public Object traceCachePutOperation(ProceedingJoinPoint joinPoint, CachePut cachePut) throws Throwable {
        String cacheName = resolveCacheName(cachePut.cacheNames(), cachePut.value(), joinPoint);
        return traceCacheOperation(joinPoint, cacheName, "put", null, null);
    }

    @Around("@annotation(cacheEvict)")
    public Object traceCacheEvictOperation(ProceedingJoinPoint joinPoint, CacheEvict cacheEvict) throws Throwable {
        String cacheName = resolveCacheName(cacheEvict.cacheNames(), cacheEvict.value(), joinPoint);
        return traceCacheOperation(joinPoint, cacheName, "evict", null, span -> {
            span.setAttribute("cache.evict_all_entries", cacheEvict.allEntries());
            span.setAttribute("cache.evict_before_invocation", cacheEvict.beforeInvocation());
        });
    }

    private Object traceCacheOperation(
            ProceedingJoinPoint joinPoint,
            String cacheName,
            String operation,
            Boolean cacheHit,
            Consumer<Span> additionalAttributes) throws Throwable {

        String methodName = joinPoint.getSignature().getName();
        String spanName = String.format("cache.%s", operation);
        Span span = tracer.spanBuilder(spanName).startSpan();
        long startTime = System.nanoTime();
        Scope scope = null;

        try {
            scope = span.makeCurrent();
        } catch (Throwable scopeError) {
            span.recordException(scopeError);
            span.setAttribute("error", true);
            span.setAttribute("error.type", scopeError.getClass().getSimpleName());
            span.end();
            throw scopeError;
        }

        try {
            span.setAttribute("cache.name", cacheName);
            span.setAttribute("cache.operation", operation);
            span.setAttribute("cache.method", methodName);

            if (cacheHit != null) {
                span.setAttribute("cache.hit", cacheHit);
            }

            if (additionalAttributes != null) {
                additionalAttributes.accept(span);
            }

            Object result = joinPoint.proceed();

            if (result != null) {
                span.setAttribute("cache.result_type", result.getClass().getSimpleName());
            }

            return result;

        } catch (Throwable e) {
            span.recordException(e);
            span.setAttribute("error", true);
            span.setAttribute("error.type", e.getClass().getSimpleName());
            log.error("Cache operation error: {} on {}", spanName, cacheName, e);
            throw e;
        } finally {
            long durationNanos = System.nanoTime() - startTime;
            long durationMs = TimeUnit.NANOSECONDS.toMillis(durationNanos);
            span.setAttribute("cache.duration_ms", durationMs);

            if (durationMs > 100) {
                span.setAttribute("cache.slow_operation", true);
            }

            if (scope != null) {
                try {
                    scope.close();
                } catch (Throwable scopeCloseError) {
                    log.warn("Failed closing cache scope for {} on {}", spanName, cacheName, scopeCloseError);
                }
            }

            span.end();
        }
    }

    private String resolveCacheName(String[] cacheNames, String[] aliases, ProceedingJoinPoint joinPoint) {
        String resolved = firstNonBlank(cacheNames);
        if (resolved == null) {
            resolved = firstNonBlank(aliases);
        }
        if (resolved != null) {
            return resolved;
        }
        // Fall back to method signature when no cache name is provided.
        return joinPoint.getSignature().toShortString();
    }

    private String firstNonBlank(String[] candidates) {
        if (candidates == null) {
            return null;
        }
        for (String candidate : candidates) {
            if (candidate != null && !candidate.isBlank()) {
                return candidate;
            }
        }
        return null;
    }
}
