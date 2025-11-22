package com.rcs.ssf.tracing;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

/**
 * AOP aspect for automatic span instrumentation of GraphQL resolvers.
 * 
 * Intercepts methods annotated with @QueryMapping, @MutationMapping,
 * @SubscriptionMapping to automatically create spans with:
 * - Span name: graphql.<query|mutation|subscription>.<method_name>
 * - Attributes: parameter types, return type, execution time
 * - Exception tracking and span status
 * 
 * This provides automatic tracing without requiring manual @WithSpan annotations.
 * Complements OpenTelemetry instrumentation for GraphQL queries.
 * 
 * Usage:
 * @QueryMapping
 * public UserDto getCurrentUser() {
 *     // Automatically traced with span: graphql.query.getCurrentUser
 * }
 */
@Aspect
@Component
@Slf4j
public class GraphQLResolverInstrumentation {

    private final Tracer tracer;

    public GraphQLResolverInstrumentation(Tracer tracer) {
        this.tracer = tracer;
    }

    /**
     * Trace GraphQL query resolvers (@QueryMapping).
     */
    @Around("@annotation(org.springframework.graphql.data.method.annotation.QueryMapping)")
    public Object traceQueryResolver(ProceedingJoinPoint joinPoint) throws Throwable {
        return traceResolver("graphql.query", joinPoint);
    }

    /**
     * Trace GraphQL mutation resolvers (@MutationMapping).
     */
    @Around("@annotation(org.springframework.graphql.data.method.annotation.MutationMapping)")
    public Object traceMutationResolver(ProceedingJoinPoint joinPoint) throws Throwable {
        return traceResolver("graphql.mutation", joinPoint);
    }

    /**
     * Trace GraphQL subscription resolvers (@SubscriptionMapping).
     */
    @Around("@annotation(org.springframework.graphql.data.method.annotation.SubscriptionMapping)")
    public Object traceSubscriptionResolver(ProceedingJoinPoint joinPoint) throws Throwable {
        return traceResolver("graphql.subscription", joinPoint);
    }

    /**
     * Core tracing logic for GraphQL resolvers.
     * 
     * Creates span with:
     * - Name: <operation_type>.<method_name>
     * - Attributes: method name, class name, parameter count, return type
     * - Error tracking
     * - Execution time
     */
    private Object traceResolver(String operationType, ProceedingJoinPoint joinPoint) throws Throwable {
        String methodName = joinPoint.getSignature().getName();
        
        // Safely get class name: check if target exists, otherwise fall back to declaring type
        String className = "UnknownClass";
        if (joinPoint.getTarget() != null) {
            className = joinPoint.getTarget().getClass().getSimpleName();
        } else {
            Class<?> declaringClass = joinPoint.getSignature().getDeclaringType();
            if (declaringClass != null) {
                className = declaringClass.getSimpleName();
            }
        }
        
        String spanName = String.format("%s.%s", operationType, methodName);

        Span span = tracer
            .spanBuilder(spanName)
            .setParent(Context.current())
            .setSpanKind(SpanKind.INTERNAL)
            .startSpan();

        try (Scope scope = span.makeCurrent()) {
            // Add resolver context attributes
            span.setAttribute("resolver.class", className);
            span.setAttribute("resolver.method", methodName);
            String type = operationType.substring(operationType.lastIndexOf('.') + 1);
            span.setAttribute("resolver.type", type);
            span.setAttribute("resolver.parameter_count", joinPoint.getArgs().length);

            // Execute resolver
            Object result = joinPoint.proceed();

            // Record result attributes
            if (result != null) {
                span.setAttribute("resolver.result_type", result.getClass().getSimpleName());
                span.setAttribute("resolver.result_available", true);
            } else {
                span.setAttribute("resolver.result_available", false);
            }

            return result;

        } catch (Throwable throwable) {
            span.recordException(throwable);
            span.setStatus(StatusCode.ERROR);

            log.error("GraphQL resolver error: {} at {}", spanName, className, throwable);
            throw throwable;

        } finally {
            span.end();
        }
    }
}
