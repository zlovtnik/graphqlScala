package com.rcs.ssf.http.filter;

/**
 * Thread-safe holder for request trace context (request ID, user ID, span ID).
 *
 * <p>Stores trace context in a {@link ThreadLocal} so downstream service and repository
 * layers can correlate logs with the active HTTP request. Servlet containers reuse
 * worker threads, so callers <strong>must</strong> clear the context once request
 * processing completes to avoid leaking request-scoped data between users or causing
 * classloader retention. Always wrap filter/controller work in a {@code try/finally}
 * block and call {@link #clear()} in the {@code finally} block.</p>
 *
 * <pre>
 * try {
 *     TraceContextHolder.setRequestId(requestId);
 *     TraceContextHolder.setUserId(userId);
 *     filterChain.doFilter(request, response);
 * } finally {
 *     TraceContextHolder.clear();
 * }
 * </pre>
 */
public class TraceContextHolder {

    private static final ThreadLocal<TraceContext> CONTEXT = new ThreadLocal<>();

    private TraceContextHolder() {
        // Utility class - prevent instantiation
    }

    /**
     * Set request ID for current thread.
     */
    public static void setRequestId(String requestId) {
        TraceContext context = getOrCreateContext();
        context.setRequestId(requestId);
    }

    /**
     * Get request ID for current thread.
     */
    public static String getRequestId() {
        TraceContext context = CONTEXT.get();
        return context != null ? context.getRequestId() : null;
    }

    /**
     * Set user ID for current thread.
     */
    public static void setUserId(String userId) {
        TraceContext context = getOrCreateContext();
        context.setUserId(userId);
    }

    /**
     * Get user ID for current thread.
     */
    public static String getUserId() {
        TraceContext context = CONTEXT.get();
        return context != null ? context.getUserId() : null;
    }

    /**
     * Set span ID for current thread (populated by OpenTelemetry).
     */
    public static void setSpanId(String spanId) {
        TraceContext context = getOrCreateContext();
        context.setSpanId(spanId);
    }

    /**
     * Get span ID for current thread.
     */
    public static String getSpanId() {
        TraceContext context = CONTEXT.get();
        return context != null ? context.getSpanId() : null;
    }

    /**
     * Set trace ID for current thread (populated by OpenTelemetry).
     */
    public static void setTraceId(String traceId) {
        TraceContext context = getOrCreateContext();
        context.setTraceId(traceId);
    }

    /**
     * Get trace ID for current thread.
     */
    public static String getTraceId() {
        TraceContext context = CONTEXT.get();
        return context != null ? context.getTraceId() : null;
    }

    /**
     * Get all context data as a snapshot for logging.
     */
    public static TraceContext getContext() {
        TraceContext context = CONTEXT.get();
        return context != null ? new TraceContext(context) : new TraceContext();
    }

    /**
     * Clear all context data (called at end of request).
     */
    public static void clear() {
        CONTEXT.remove();
    }

    private static TraceContext getOrCreateContext() {
        TraceContext context = CONTEXT.get();
        if (context == null) {
            context = new TraceContext();
            CONTEXT.set(context);
        }
        return context;
    }

    /**
     * Inner class holding trace context data.
     * Fields are private to enforce encapsulation; access via getters/setters only.
     */
    public static class TraceContext {
        private String requestId;
        private String userId;
        private String traceId;
        private String spanId;

        public TraceContext() {
        }

        public TraceContext(TraceContext other) {
            if (other == null) {
                return;
            }
            this.requestId = other.requestId;
            this.userId = other.userId;
            this.traceId = other.traceId;
            this.spanId = other.spanId;
        }

        /**
         * Get request ID.
         */
        public String getRequestId() {
            return requestId;
        }

        /**
         * Set request ID (package-private; use TraceContextHolder.setRequestId() publicly).
         */
        void setRequestId(String requestId) {
            this.requestId = requestId;
        }

        /**
         * Get user ID.
         */
        public String getUserId() {
            return userId;
        }

        /**
         * Set user ID (package-private; use TraceContextHolder.setUserId() publicly).
         */
        void setUserId(String userId) {
            this.userId = userId;
        }

        /**
         * Get trace ID.
         */
        public String getTraceId() {
            return traceId;
        }

        /**
         * Set trace ID (package-private; use TraceContextHolder.setTraceId() publicly).
         */
        void setTraceId(String traceId) {
            this.traceId = traceId;
        }

        /**
         * Get span ID.
         */
        public String getSpanId() {
            return spanId;
        }

        /**
         * Set span ID (package-private; use TraceContextHolder.setSpanId() publicly).
         */
        void setSpanId(String spanId) {
            this.spanId = spanId;
        }

        @Override
        public String toString() {
            return "TraceContext{" +
                    "requestId='" + requestId + '\'' +
                    ", traceId='" + traceId + '\'' +
                    ", spanId='" + spanId + '\'' +
                    ", userId='<redacted>'" +
                    '}';
        }

        /**
         * Returns the full context, including the user identifier. Only call this from
         * secured diagnostics tooling where including PII is acceptable.
         */
        public String toDetailedString() {
            return "TraceContext{" +
                    "requestId='" + requestId + '\'' +
                    ", userId='" + userId + '\'' +
                    ", traceId='" + traceId + '\'' +
                    ", spanId='" + spanId + '\'' +
                    '}';
        }
    }
}
