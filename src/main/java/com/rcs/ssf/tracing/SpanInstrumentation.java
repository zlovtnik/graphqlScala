package com.rcs.ssf.tracing;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;

import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Utility for instrumenting code with OpenTelemetry spans.
 * 
 * Provides convenient methods for creating and managing spans without manual
 * scope management. Automatically handles span lifecycle and error recording.
 * 
 * Usage:
 * SpanInstrumentation.runInSpan(tracer, "operation.name", () -> {
 *     // Code to trace
 * });
 * 
 * Object result = SpanInstrumentation.executeInSpan(tracer, "operation.name", () -> {
 *     return performOperation();
 * });
 */
public class SpanInstrumentation {

    private SpanInstrumentation() {
        // Utility class - prevent instantiation
    }

    /**
     * Execute a runnable within a span context.
     * 
     * Automatically creates a span, manages scope, and records exceptions.
     * 
        * @param tracer The OpenTelemetry tracer
        * @param spanName Name of the span (e.g., "graphql.query.user")
        * @param runnable Code to execute within span
        * @throws InstrumentationException if the runnable throws and span instrumentation wraps it
     */
    public static void runInSpan(Tracer tracer, String spanName, Runnable runnable) {
        Span span = tracer.spanBuilder(spanName).startSpan();
        try (Scope scope = span.makeCurrent()) {
            runnable.run();
        } catch (Exception e) {
            throw handleSpanException(span, spanName, e);
        } finally {
            span.end();
        }
    }

    /**
     * Execute a callable within a span context and return result.
     * 
     * Automatically creates a span, manages scope, records result, and handles exceptions.
     * 
        * @param tracer The OpenTelemetry tracer
        * @param spanName Name of the span (e.g., "db.query.users")
        * @param callable Code to execute within span
        * @param <T> Return type
        * @return Result from callable
        * @throws InstrumentationException if the callable throws
     */
    public static <T> T executeInSpan(Tracer tracer, String spanName, Callable<T> callable) {
        Span span = tracer.spanBuilder(spanName).startSpan();
        try (Scope scope = span.makeCurrent()) {
            T result = callable.call();
            span.setAttribute("result.available", result != null);
            return result;
        } catch (Exception e) {
            throw handleSpanException(span, spanName, e);
        } finally {
            span.end();
        }
    }

    /**
     * Execute a function within a span context.
     * 
        * @param tracer The OpenTelemetry tracer
        * @param spanName Name of the span
        * @param function Function to execute
        * @param input Input to function
        * @param <T> Input type
        * @param <R> Return type
        * @return Result from function
        * @throws InstrumentationException if the function throws
     */
    public static <T, R> R executeInSpan(Tracer tracer, String spanName, Function<T, R> function, T input) {
        Span span = tracer.spanBuilder(spanName).startSpan();
        try (Scope scope = span.makeCurrent()) {
            R result = function.apply(input);
            span.setAttribute("result.available", result != null);
            return result;
        } catch (Exception e) {
            throw handleSpanException(span, spanName, e);
        } finally {
            span.end();
        }
    }

    /**
     * Execute a consumer within a span context.
     * 
        * @param tracer The OpenTelemetry tracer
        * @param spanName Name of the span
        * @param consumer Consumer to execute
        * @param input Input to consumer
        * @param <T> Input type
        * @throws InstrumentationException if the consumer throws
     */
    public static <T> void consumeInSpan(Tracer tracer, String spanName, Consumer<T> consumer, T input) {
        Span span = tracer.spanBuilder(spanName).startSpan();
        try (Scope scope = span.makeCurrent()) {
            consumer.accept(input);
        } catch (Exception e) {
            throw handleSpanException(span, spanName, e);
        } finally {
            span.end();
        }
    }

    /**
     * Add attributes to the current span.
     * 
     * @param key Attribute key
     * @param value Attribute value
     */
    public static void addAttribute(String key, String value) {
        Span.current().setAttribute(key, value);
    }

    /**
     * Add a long attribute to the current span.
     * 
     * @param key Attribute key
     * @param value Attribute value
     */
    public static void addAttribute(String key, long value) {
        Span.current().setAttribute(key, value);
    }

    /**
     * Add a double attribute to the current span.
     * 
     * @param key Attribute key
     * @param value Attribute value
     */
    public static void addAttribute(String key, double value) {
        Span.current().setAttribute(key, value);
    }

    /**
     * Add a boolean attribute to the current span.
     * 
     * @param key Attribute key
     * @param value Attribute value
     */
    public static void addAttribute(String key, boolean value) {
        Span.current().setAttribute(key, value);
    }

    /**
     * Record an exception in the current span.
     * 
     * @param exception Exception to record
     */
    public static void recordException(Exception exception) {
        Span span = Span.current();
        span.recordException(exception);
        span.setStatus(StatusCode.ERROR);
        span.setAttribute("exception.type", exception.getClass().getSimpleName());
        if (exception.getMessage() != null) {
            span.setAttribute("exception.message", exception.getMessage());
        }
    }

    /**
     * Add event to current span without attributes.
     *
     * @param eventName Event name
     */
    public static void addEvent(String eventName) {
        Span.current().addEvent(eventName);
    }

    /**
     * Add event to current span with attributes.
     *
     * @param eventName Event name
     * @param attributes Attributes to attach to the event
     */
    public static void addEvent(String eventName, Attributes attributes) {
        Span.current().addEvent(eventName, attributes);
    }

    private static InstrumentationException handleSpanException(Span span, String spanName, Exception exception) {
        span.recordException(exception);
        span.setStatus(StatusCode.ERROR);
        span.setAttribute("exception.type", exception.getClass().getSimpleName());
        if (exception.getMessage() != null) {
            span.setAttribute("exception.message", exception.getMessage());
        }
        return new InstrumentationException("Error in span: " + spanName, exception);
    }

    public static class InstrumentationException extends RuntimeException {
        public InstrumentationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
