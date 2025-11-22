package com.rcs.ssf.tracing;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;

/**
 * AOP aspect for automatic span instrumentation of database operations.
 * 
 * Intercepts repository method calls to automatically create spans with:
 * - Span name: db.query.<operation_type> (find, save, delete, etc.)
 * - Attributes: table name, operation type, row count, execution time
 * - Exception tracking and span status
 * 
 * Operations traced:
 * - findById, findAll, findByUsername, etc. (SELECT)
 * - save (INSERT/UPDATE)
 * - delete, deleteById (DELETE)
 * 
 * Complements SQL query logging with semantic span information.
 * 
 * Usage (automatic via OtelConfig):
 * User user = userRepository.findById(123);  // Traced as db.query.find
 * 
 * Bean instantiation: Created by OtelConfig.databaseOperationInstrumentation() factory method.
 */
@Aspect
@Slf4j
public class DatabaseOperationInstrumentation {

    private final Tracer tracer;
    /**
     * Threshold (in milliseconds) above which database queries are flagged as slow.
     * Default: 1000ms. Can be overridden for testing via constructor.
     */
    private final long slowQueryThresholdMs;

    /**
     * Constructor for bean creation via OtelConfig factory method.
     * Initializes slow-query threshold with default of 1000ms.
     * 
     * @param tracer the OpenTelemetry tracer (injected via factory method)
     */
    public DatabaseOperationInstrumentation(Tracer tracer) {
        this(tracer, 1000L);
    }

    /**
     * Constructor for testing; allows explicit threshold override.
     * 
     * @param tracer the OpenTelemetry tracer
     * @param slowQueryThresholdMs threshold in milliseconds; must be positive (> 0)
     * @throws IllegalArgumentException if threshold is not positive
     */
    private DatabaseOperationInstrumentation(Tracer tracer, long slowQueryThresholdMs) {
        if (slowQueryThresholdMs <= 0) {
            throw new IllegalArgumentException("slowQueryThresholdMs must be positive, got: " + slowQueryThresholdMs);
        }
        this.tracer = tracer;
        this.slowQueryThresholdMs = slowQueryThresholdMs;
    }

    /**
     * Factory method for creating instances with custom slow-query threshold (for testing).
     * 
     * @param tracer the OpenTelemetry tracer
     * @param slowQueryThresholdMs custom threshold in milliseconds; must be positive (> 0)
     * @return new instance with custom threshold
     * @throws IllegalArgumentException if threshold is not positive
     */
    public static DatabaseOperationInstrumentation withThreshold(Tracer tracer, long slowQueryThresholdMs) {
        return new DatabaseOperationInstrumentation(tracer, slowQueryThresholdMs);
    }

    /**
     * Trace all repository method calls.
     * 
     * Identifies operation type from method name and creates appropriate span.
     */
    @Around("execution(* com.rcs.ssf.repository.*.*(..)) && " +
            "!execution(* com.rcs.ssf.repository.*.hashCode(..)) && " +
            "!execution(* com.rcs.ssf.repository.*.equals(..))")
    public Object traceRepositoryOperation(ProceedingJoinPoint joinPoint) throws Throwable {
        String methodName = joinPoint.getSignature().getName();
        String className = joinPoint.getSignature().getDeclaringType().getSimpleName();
        String repositoryName = className.replace("Repository", "").toLowerCase();

        // Determine operation type from method name
        String operationType = inferOperationType(methodName);
        String spanName = String.format("db.query.%s", operationType);

        Span span = tracer.spanBuilder(spanName).startSpan();
        long startTime = System.currentTimeMillis();

        try (Scope scope = span.makeCurrent()) {
            // Add database operation attributes
            span.setAttribute("db.repository", className);
            span.setAttribute("db.entity", repositoryName);
            span.setAttribute("db.operation", operationType);
            span.setAttribute("db.method", methodName);

            // Add parameter information if available
            Object[] args = joinPoint.getArgs();
            if (args.length > 0) {
                span.setAttribute("db.params.count", args.length);
                for (int i = 0; i < Math.min(args.length, 3); i++) {
                    Object arg = args[i];
                    if (arg != null) {
                        span.setAttribute("db.params." + i, arg.getClass().getSimpleName());
                    }
                }
            }

            // Execute database operation
            Object result = joinPoint.proceed();

            // Record result attributes
            if (result != null) {
                span.setAttribute("db.result_type", result.getClass().getSimpleName());
                
                // Try to extract row count from result
                if (result instanceof java.util.Collection<?>) {
                    span.setAttribute("db.row_count", ((java.util.Collection<?>) result).size());
                } else if (result instanceof java.util.Optional<?>) {
                    span.setAttribute("db.result_available", ((java.util.Optional<?>) result).isPresent());
                } else if (result instanceof Boolean) {
                    span.setAttribute("db.result_bool", (Boolean) result);
                } else if (result instanceof Number) {
                    span.setAttribute("db.result_count", ((Number) result).longValue());
                }
            }

            return result;

        } catch (Throwable throwable) {
            span.recordException(throwable);
            span.setAttribute("error", true);
            span.setAttribute("error.type", throwable.getClass().getSimpleName());
            if (throwable.getMessage() != null) {
                span.setAttribute("error.message", throwable.getMessage());
            }
            // Mark the span with OpenTelemetry error status
            span.setStatus(StatusCode.ERROR, throwable.getMessage() != null ? throwable.getMessage() : "database operation failed");

            log.error("Database operation error: {} in {}", spanName, className, throwable);
            throw throwable;

        } finally {
            long duration = System.currentTimeMillis() - startTime;
            span.setAttribute("db.duration_ms", duration);
            
            // Add latency percentile hint (used for alerts)
            if (duration > slowQueryThresholdMs) {
                span.setAttribute("db.latency_warning", "slow_query");
            }
            
            span.end();
        }
    }

    /**
     * Infer database operation type from method name.
     * 
     * Examples:
     * - findById → find
     * - findByUsername → find
     * - save → insert/update
     * - delete → delete
     * - count → aggregate
     */
    private String inferOperationType(String methodName) {
        if (methodName.startsWith("find")) {
            return "find";
        } else if (methodName.startsWith("query")) {
            return "query";
        } else if (methodName.startsWith("save")) {
            return "insert_update";
        } else if (methodName.startsWith("delete")) {
            return "delete";
        } else if (methodName.startsWith("count")) {
            return "aggregate";
        } else if (methodName.startsWith("exists")) {
            return "exists";
        } else if (methodName.startsWith("get")) {
            return "find";
        } else {
            return "other";
        }
    }
}
