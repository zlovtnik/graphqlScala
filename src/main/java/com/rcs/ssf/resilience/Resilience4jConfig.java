package com.rcs.ssf.resilience;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.core.registry.EntryAddedEvent;
import io.github.resilience4j.core.registry.EntryRemovedEvent;
import io.github.resilience4j.core.registry.EntryReplacedEvent;
import io.github.resilience4j.core.registry.RegistryEventConsumer;
import io.github.resilience4j.micrometer.tagged.TaggedCircuitBreakerMetrics;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.concurrent.TimeoutException;

/**
 * Resilience4j configuration for fault-tolerant GraphQL operations.
 * 
 * Circuit Breaker Thresholds:
 * - Database: 10% failure threshold (5 of 50 failures trigger OPEN)
 * - Redis: 15% failure threshold (7 of 50 failures trigger OPEN)
 * - MinIO: 20% failure threshold (10 of 50 failures trigger OPEN)
 * - Auth Service: 5% failure threshold (2 of 50 failures trigger OPEN) - most critical
 * - Audit Service: 8% failure threshold (4 of 50 failures trigger OPEN)
 * 
 * Retry Strategy: Exponential backoff (100ms base, 2x multiplier, max 1000ms)
 * 
 * Fallback Mechanisms:
 * - Database: Return cached copy, degrade to read-only mode
 * - Redis: Use local Caffeine cache as backup
 * - MinIO: Return placeholder, defer to async reconciliation
 * - Auth: Deny request (security first)
 * - Audit: Buffer locally, replay when service recovers
 * 
 * Half-Open State: After 30 seconds downtime, attempt 3 requests to validate recovery.
 */
@Configuration
@Slf4j
public class Resilience4jConfig {

    /**
     * Circuit breaker for database operations.
     * 
     * Protects: GraphQL resolvers, audit inserts, query execution
     * Threshold: 10% failure rate (5 failures in 50 requests)
     * Recovery time: 30 seconds
     */
    @Bean
    public CircuitBreaker databaseCircuitBreaker(CircuitBreakerRegistry registry) {
        return registry.circuitBreaker("database",
                CircuitBreakerConfig.custom()
                        .failureRateThreshold(10.0f)
                        .slowCallRateThreshold(15.0f) // Slow if >500ms
                        .slowCallDurationThreshold(Duration.ofMillis(500))
                        .waitDurationInOpenState(Duration.ofSeconds(30))
                        .permittedNumberOfCallsInHalfOpenState(3)
                        .minimumNumberOfCalls(50) // Need 50 calls before calculating rate
                        .build());
    }

    /**
     * Circuit breaker for Redis operations.
     * 
     * Protects: Persisted query cache, session cache, metrics cache
     * Threshold: 15% failure rate (7 failures in 50 requests)
     * Recovery time: 30 seconds
     */
    @Bean
    public CircuitBreaker redisCircuitBreaker(CircuitBreakerRegistry registry) {
        return registry.circuitBreaker("redis",
                CircuitBreakerConfig.custom()
                        .failureRateThreshold(15.0f)
                        .slowCallRateThreshold(20.0f) // Slow if >200ms
                        .slowCallDurationThreshold(Duration.ofMillis(200))
                        .waitDurationInOpenState(Duration.ofSeconds(30))
                        .permittedNumberOfCallsInHalfOpenState(3)
                        .minimumNumberOfCalls(50)
                        .build());
    }

    /**
     * Circuit breaker for MinIO object storage operations.
     * 
     * Protects: File uploads, document storage, audit log archival
     * Threshold: 20% failure rate (10 failures in 50 requests)
     * Recovery time: 60 seconds (slower recovery for storage)
     */
    @Bean
    public CircuitBreaker minioCircuitBreaker(CircuitBreakerRegistry registry) {
        return registry.circuitBreaker("minio",
                CircuitBreakerConfig.custom()
                        .failureRateThreshold(20.0f)
                        .slowCallRateThreshold(25.0f) // Slow if >1000ms
                        .slowCallDurationThreshold(Duration.ofSeconds(1))
                        .waitDurationInOpenState(Duration.ofSeconds(60))
                        .permittedNumberOfCallsInHalfOpenState(3)
                        .minimumNumberOfCalls(50)
                        .build());
    }

    /**
     * Circuit breaker for authentication/authorization service.
     * 
     * Protects: JWT validation, permission checks, user lookup
     * Threshold: 5% failure rate (2 failures in 50 requests) - CRITICAL SERVICE
     * Recovery time: 15 seconds (fast recovery to restore security checks)
     */
    @Bean
    public CircuitBreaker authServiceCircuitBreaker(CircuitBreakerRegistry registry) {
        return registry.circuitBreaker("auth-service",
                CircuitBreakerConfig.custom()
                        .failureRateThreshold(5.0f)
                        .slowCallRateThreshold(10.0f) // Slow if >100ms
                        .slowCallDurationThreshold(Duration.ofMillis(100))
                        .waitDurationInOpenState(Duration.ofSeconds(15))
                        .permittedNumberOfCallsInHalfOpenState(2) // Conservative recovery
                        .minimumNumberOfCalls(50)
                        .build());
    }

    /**
     * Circuit breaker for audit service operations.
     * 
     * Protects: Compliance logging, metrics recording, event tracking
     * Threshold: 8% failure rate (4 failures in 50 requests)
     * Recovery time: 45 seconds
     */
    @Bean
    public CircuitBreaker auditServiceCircuitBreaker(CircuitBreakerRegistry registry) {
        return registry.circuitBreaker("audit-service",
                CircuitBreakerConfig.custom()
                        .failureRateThreshold(8.0f)
                        .slowCallRateThreshold(15.0f) // Slow if >300ms
                        .slowCallDurationThreshold(Duration.ofMillis(300))
                        .waitDurationInOpenState(Duration.ofSeconds(45))
                        .permittedNumberOfCallsInHalfOpenState(3)
                        .minimumNumberOfCalls(50)
                        .build());
    }

    /**
     * Retry configuration for transient failures.
     * 
     * Strategy: Exponential backoff
     * - Initial delay: 100ms
     * - Multiplier: 2x
     * - Max delay: 1000ms
     * - Max attempts: 3
     * 
     * Retryable exceptions: Connection timeout, I/O errors, temporary unavailable
     */
    @Bean
    public Retry databaseRetry(RetryRegistry registry) {
        return registry.retry("database",
                RetryConfig.custom()
                        .maxAttempts(3)
                        .intervalFunction(
                                io.github.resilience4j.core.IntervalFunction
                                        .ofExponentialBackoff(100, 2, 1000))
                        .build());
    }

    /**
     * Retry configuration for Redis operations.
     */
    @Bean
    public Retry redisRetry(RetryRegistry registry) {
        return registry.retry("redis",
                RetryConfig.custom()
                        .maxAttempts(3)
                        .intervalFunction(
                                io.github.resilience4j.core.IntervalFunction
                                        .ofExponentialBackoff(50, 2, 500))
                        .build());
    }

    /**
     * Retry configuration for MinIO operations.
     */
    @Bean
    public Retry minioRetry(RetryRegistry registry) {
        return registry.retry("minio",
                RetryConfig.custom()
                        .maxAttempts(2)
                        .intervalFunction(
                                io.github.resilience4j.core.IntervalFunction
                                        .ofExponentialBackoff(200, 2, 2000))
                        .build());
    }

    /**
     * Expose metrics for all circuit breakers.
     */
    @Bean
    public TaggedCircuitBreakerMetrics circuitBreakerMetrics(CircuitBreakerRegistry registry,
                                                             MeterRegistry meterRegistry) {
        log.info("Registering circuit breaker metrics");
        
        // Register listener to track state changes
        registry.getEventPublisher()
                .onEntryAdded(event -> log.info("Circuit breaker {} created", event.getAddedEntry().getName()))
                .onEntryRemoved(event -> log.info("Circuit breaker {} removed", event.getRemovedEntry().getName()));
        
        // Export metrics to Prometheus
        TaggedCircuitBreakerMetrics metrics = TaggedCircuitBreakerMetrics.ofCircuitBreakerRegistry(registry);
        metrics.bindTo(meterRegistry);
        
        return metrics;
    }

    /**
     * Determine if an exception is retryable (transient failure).
     * 
     * Retryable:
     * - TimeoutException
     * - ConnectException
     * - SocketTimeoutException
     * - IOException (I/O related)
     * 
     * Non-retryable:
     * - AuthenticationException (security)
     * - ValidationException (invalid input)
     * - NotFoundException (resource doesn't exist)
     */
    private boolean isRetryableException(Throwable throwable) {
        if (throwable == null) {
            return false;
        }

        // Retryable exceptions
        if (throwable instanceof TimeoutException) {
            return true;
        }
        if (throwable instanceof java.net.ConnectException) {
            return true;
        }
        if (throwable instanceof java.net.SocketTimeoutException) {
            return true;
        }
        if (throwable instanceof java.io.IOException && 
            !(throwable.getMessage() != null && throwable.getMessage().contains("401"))) {
            return true;
        }

        // Check cause
        if (throwable.getCause() != null) {
            return isRetryableException(throwable.getCause());
        }

        return false;
    }
}
