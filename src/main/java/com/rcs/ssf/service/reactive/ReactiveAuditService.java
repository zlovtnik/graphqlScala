package com.rcs.ssf.service.reactive;

import com.rcs.ssf.util.HashUtils;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.TimeoutException;

/**
 * Reactive audit service for non-blocking database operations.
 * 
 * Features:
 * - Non-blocking inserts/updates to audit tables via R2DBC
 * - Backpressure handling for high-volume audit events
 * - Automatic retry with exponential backoff for transient failures
 * - Timeout protection (5 seconds default per operation)
 * - Comprehensive metrics tracking
 * 
 * Usage:
 * auditService.logGraphQLComplexity(query, score)
 *     .subscribeOn(Schedulers.boundedElastic())
 *     .subscribe(success -> log.info("Audit logged"), error -> log.error("Audit failed", error));
 * 
 * Backpressure behavior: Dropped events are counted as audit.dropped_events_total metric
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReactiveAuditService {

    private final R2dbcEntityTemplate template;
    private final MeterRegistry meterRegistry;

    /**
     * GraphQL query complexity threshold for alerting/rejection.
     * Configurable via application properties: graphql.complexity.threshold
     * Default: 5000 (queries exceeding this score are marked as EXCEEDED in audit logs)
     */
    @Value("${graphql.complexity.threshold:5000}")
    private int complexityThreshold;

    private static final Duration OPERATION_TIMEOUT = Duration.ofSeconds(5);
    private static final int MAX_RETRIES = 3;

    public Mono<Void> logGraphQLComplexity(String query, int complexity) {
        Timer.Sample sample = Timer.start(meterRegistry);
        
        String queryHash = generateHash(query);
        String status = complexity > complexityThreshold ? "EXCEEDED" : "OK";
        
        log.debug("Recording complexity score {} for query hash {} (threshold: {})", complexity, queryHash, complexityThreshold);
        
        return template.getDatabaseClient()
                .sql("INSERT INTO audit_graphql_complexity (query_hash, complexity, threshold, status, timestamp) VALUES (?, ?, ?, ?, ?)")
                .bind(0, queryHash)
                .bind(1, complexity)
                .bind(2, complexityThreshold)
                .bind(3, status)
                .bind(4, LocalDateTime.now())
                .fetch()
                .rowsUpdated()
                .then()
                .timeout(OPERATION_TIMEOUT)
                .retryWhen(Retry.backoff(MAX_RETRIES, Duration.ofMillis(100))
                        .doBeforeRetry(signal -> 
                            log.info("Retrying complexity audit (attempt {}/{})", 
                                    signal.totalRetries() + 1, MAX_RETRIES)))
                .onErrorResume(TimeoutException.class, throwable -> {
                    log.warn("Audit insert timeout: {}", throwable.getMessage());
                    meterRegistry.counter("audit.graphql_complexity.errors_total").increment();
                    return Mono.empty();
                })
                .onErrorResume(throwable -> {
                    log.warn("Failed to log complexity: {}", throwable.getMessage());
                    meterRegistry.counter("audit.graphql_complexity.errors_total").increment();
                    return Mono.empty(); // Continue despite failure
                })
                .doFinally(signalType -> {
                    sample.stop(Timer.builder("audit.graphql_complexity.duration_ms")
                            .publishPercentiles(0.5, 0.95, 0.99)
                            .register(meterRegistry));
                    meterRegistry.counter("audit.graphql_complexity_total").increment();
                });
    }

    /**
     * Log circuit breaker state transition (non-blocking).
     * 
     * @param serviceName The service being protected (e.g., "database", "redis")
     * @param newState The new circuit breaker state (CLOSED, OPEN, HALF_OPEN)
     * @return Mono that completes when audit record is inserted
     */
    public Mono<Void> logCircuitBreakerEvent(String serviceName, String newState) {
        Timer.Sample sample = Timer.start(meterRegistry);
        
        return Mono.defer(() -> {
            log.debug("Recording circuit breaker {} transition to {}", serviceName, newState);
            
            return template.getDatabaseClient()
                    .sql("INSERT INTO audit_circuit_breaker_events (breaker_name, service_name, state_transition, event_timestamp) VALUES (?, ?, ?, ?)")
                    .bind(0, serviceName)
                    .bind(1, serviceName)
                    .bind(2, newState)
                    .bind(3, LocalDateTime.now())
                    .fetch()
                    .rowsUpdated()
                    .then()
                    .timeout(OPERATION_TIMEOUT, Mono.error(
                            new TimeoutException("Circuit breaker audit timeout")))
                    .onErrorResume(throwable -> {
                        log.warn("Failed to log circuit breaker event: {}", throwable.getMessage());
                        meterRegistry.counter("audit.circuit_breaker.errors_total").increment();
                        return Mono.empty();
                    });
        })
        .retryWhen(Retry.backoff(MAX_RETRIES, Duration.ofMillis(100))
                .doBeforeRetry(signal -> 
                    log.info("Retrying circuit breaker audit (attempt {}/{})", 
                            signal.totalRetries() + 1, MAX_RETRIES)))
        .doFinally(signalType -> {
            sample.stop(Timer.builder("audit.circuit_breaker_event.duration_ms")
                    .tag("service", serviceName)
                    .publishPercentiles(0.5, 0.95, 0.99)
                    .register(meterRegistry));
            meterRegistry.counter("audit.circuit_breaker_events_total", "service", serviceName).increment();
        });
    }

    /**
     * Log HTTP compression event (non-blocking).
     * 
     * @param algorithm The compression algorithm (gzip, br)
     * @param originalSize Original response size in bytes
     * @param compressedSize Compressed response size in bytes
     * @return Mono that completes when audit record is inserted
     */
    public Mono<Void> logCompressionEvent(String algorithm, long originalSize, long compressedSize) {
        Timer.Sample sample = Timer.start(meterRegistry);
        
        double compressionRatio = originalSize > 0 ? (double) compressedSize / originalSize : 1.0;
        
        return Mono.defer(() -> {
            log.debug("Recording {} compression: {} bytes -> {} bytes (ratio: {})", 
                    algorithm, originalSize, compressedSize, String.format("%.2f", compressionRatio));
            
            return template.getDatabaseClient()
                    .sql("INSERT INTO audit_http_compression (compression_algorithm, original_size, compressed_size, compression_ratio, recorded_at) VALUES (?, ?, ?, ?, ?)")
                    .bind(0, algorithm)
                    .bind(1, originalSize)
                    .bind(2, compressedSize)
                    .bind(3, compressionRatio)
                    .bind(4, LocalDateTime.now())
                    .fetch()
                    .rowsUpdated()
                    .then()
                    .timeout(OPERATION_TIMEOUT, Mono.error(
                            new TimeoutException("Compression audit timeout")))
                    .onErrorResume(throwable -> {
                        log.warn("Failed to log compression event: {}", throwable.getMessage());
                        meterRegistry.counter("audit.http_compression.errors_total").increment();
                        return Mono.empty();
                    });
        })
        .retryWhen(Retry.backoff(MAX_RETRIES, Duration.ofMillis(100)))
        .doFinally(signalType -> {
            sample.stop(Timer.builder("audit.http_compression.duration_ms")
                    .tag("algorithm", algorithm)
                    .publishPercentiles(0.5, 0.95, 0.99)
                    .register(meterRegistry));
            
            meterRegistry.counter("audit.http_compression_total", "algorithm", algorithm).increment();
            meterRegistry.summary("audit.http_compression.ratio").record(compressionRatio);
        });
    }

    /**
     * Log GraphQL query execution plan (non-blocking, sampled - 1 in 100).
     * 
     * @param query The GraphQL query
     * @param executionTimeMs Query execution time in milliseconds
     * @return Mono that completes when audit record is inserted (or empty if not sampled)
     */
    public Mono<Void> logExecutionPlan(String query, long executionTimeMs) {
        Timer.Sample sample = Timer.start(meterRegistry);
        
        // Sample 1 in 100 for high-volume queries
        if (Math.random() > 0.01) {
            meterRegistry.counter("audit.execution_plan.not_sampled_total").increment();
            return Mono.empty();
        }
        
        String queryHash = generateHash(query);
        
        return Mono.defer(() -> {
            log.debug("Recording execution plan for query (took {} ms)", executionTimeMs);
            
            return template.getDatabaseClient()
                    .sql("INSERT INTO audit_graphql_execution_plans (query_hash, p50_time_ms, sampled_at) VALUES (?, ?, ?)")
                    .bind(0, queryHash)
                    .bind(1, executionTimeMs)
                    .bind(2, LocalDateTime.now())
                    .fetch()
                    .rowsUpdated()
                    .then()
                    .timeout(OPERATION_TIMEOUT, Mono.error(
                            new TimeoutException("Execution plan audit timeout")))
                    .onErrorResume(throwable -> {
                        log.warn("Failed to log execution plan: {}", throwable.getMessage());
                        meterRegistry.counter("audit.execution_plan.errors_total").increment();
                        return Mono.empty();
                    });
        })
        .retryWhen(Retry.backoff(MAX_RETRIES, Duration.ofMillis(100)))
        .doFinally(signalType -> {
            sample.stop(Timer.builder("audit.execution_plan.duration_ms")
                    .publishPercentiles(0.5, 0.95, 0.99)
                    .register(meterRegistry));
            meterRegistry.counter("audit.execution_plan_total").increment();
        });
    }

    /**
     * Batch audit events with backpressure handling.
     * 
     * Emits audit.dropped_events_total metric when events are dropped due to backpressure or errors.
     * 
     * @param events Flux of audit events
     * @return Mono that completes when all batches are processed
     */
    public Mono<Void> batchLogEvents(Flux<AuditEvent> events) {
        return events
                .buffer(100) // Batch in groups of 100
                .flatMap(batch -> {
                    log.debug("Processing batch of {} audit events", batch.size());
                    return Flux.fromIterable(batch)
                            .flatMap(event -> {
                                Mono<Void> mono;
                                switch (event.getEventType()) {
                                    case "COMPLEXITY" -> mono = logGraphQLComplexity(event.getQuery(), event.getScore());
                                    case "CIRCUIT_BREAKER" -> mono = logCircuitBreakerEvent(event.getService(), event.getState());
                                    case "COMPRESSION" -> mono = logCompressionEvent(event.getAlgorithm(), event.getOriginalSize(), event.getCompressedSize());
                                    default -> mono = Mono.empty();
                                }
                                return mono;
                            })
                            .then();
                })
                .then()
                .onErrorResume(throwable -> {
                    log.error("Batch audit failed: {}", throwable.getMessage());
                    meterRegistry.counter("audit.batch.errors_total").increment();
                    meterRegistry.counter("audit.dropped_events_total").increment();
                    return Mono.empty();
                });
    }

    /**
     * Generate SHA-256 hash of GraphQL query for deduplication.
     * 
     * @param query The GraphQL query string
     * @return Hex-encoded SHA-256 hash
     * @throws IllegalStateException if SHA-256 algorithm is not available (JVM configuration issue)
     */
    private String generateHash(String query) {
        return HashUtils.sha256Hex(query);
    }

    public static class AuditEvent {
        private String eventType; // COMPLEXITY, CIRCUIT_BREAKER, COMPRESSION
        private String query;
        private Integer score;
        private String service;
        private String state;
        private String algorithm;
        private Long originalSize;
        private Long compressedSize;

        public String getEventType() { return eventType; }
        public void setEventType(String eventType) { this.eventType = eventType; }

        public String getQuery() { return query; }
        public void setQuery(String query) { this.query = query; }

        public Integer getScore() { return score; }
        public void setScore(Integer score) { this.score = score; }

        public String getService() { return service; }
        public void setService(String service) { this.service = service; }

        public String getState() { return state; }
        public void setState(String state) { this.state = state; }

        public String getAlgorithm() { return algorithm; }
        public void setAlgorithm(String algorithm) { this.algorithm = algorithm; }

        public Long getOriginalSize() { return originalSize; }
        public void setOriginalSize(Long originalSize) { this.originalSize = originalSize; }

        public Long getCompressedSize() { return compressedSize; }
        public void setCompressedSize(Long compressedSize) { this.compressedSize = compressedSize; }
    }
}
