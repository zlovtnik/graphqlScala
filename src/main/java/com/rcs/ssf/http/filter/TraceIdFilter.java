package com.rcs.ssf.http.filter;

import io.opentelemetry.api.trace.Span;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.security.Principal;
import java.util.UUID;

/**
 * Request Correlation and Tracing Filter.
 * 
 * Generates a unique request ID (X-Request-ID header) for each HTTP request
 * and propagates it via ThreadLocal for use in all service layers.
 * 
 * Features:
 * - Generates unique request ID (UUID) if not provided
 * - Sets X-Request-ID response header for client tracking
 * - Stores in ThreadLocal for propagation to services/repository layers
 * - Creates OpenTelemetry span attributes for trace correlation
 * - Exports trace context to MDC for structured logging
 * 
 * Threading Model:
 * - The filter performs explicit cleanup in a {@code finally} block by calling
 *   {@link TraceContextHolder#clear()} to avoid leaking request data across reused
 *   servlet container threads (in addition to Spring's RequestContextHolder hygiene)
 * 
 * Usage in Services:
 * String requestId = TraceContextHolder.getRequestId();
 */
@Component
@Slf4j
public class TraceIdFilter extends OncePerRequestFilter {

    public TraceIdFilter() {
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        // Extract or generate request ID
        String requestId = request.getHeader("X-Request-ID");
        if (requestId == null || requestId.isEmpty()) {
            requestId = UUID.randomUUID().toString();
        }

        // Extract or generate user ID from authentication
        String userId = extractUserId(request);

        Span currentSpan = Span.current();
        try {
            // Store in ThreadLocal for propagation to services
            TraceContextHolder.setRequestId(requestId);
            TraceContextHolder.setUserId(userId);

            // Add response header for client-side correlation
            response.setHeader("X-Request-ID", requestId);

            // Add span attributes for distributed tracing
            if (currentSpan.isRecording()) {
                currentSpan.setAttribute("http.request.id", requestId);
                currentSpan.setAttribute("http.user.id", userId != null ? userId : "anonymous");
                currentSpan.setAttribute("http.method", request.getMethod());
                currentSpan.setAttribute("http.url", request.getRequestURI());
                currentSpan.setAttribute("http.scheme", request.getScheme());
                currentSpan.setAttribute("http.client_ip", getClientIp(request));
            }

            log.debug("Trace correlation: requestId={}, userId={}, path={}", requestId, userId, request.getRequestURI());

            // Continue request chain
            filterChain.doFilter(request, response);

        } finally {
            // Record HTTP response status
            if (currentSpan.isRecording()) {
                currentSpan.setAttribute("http.status_code", response.getStatus());
            }
            
            // Clean up ThreadLocal (RequestContextHolder cleanup by Spring is preferred,
            // but explicit cleanup ensures no leaks in async contexts)
            TraceContextHolder.clear();
        }
    }

    /**
     * Extract user ID from security context.
     * Returns null if no authenticated user found.
     */
    private String extractUserId(HttpServletRequest request) {
        try {
            Principal principal = request.getUserPrincipal();
            if (principal != null && principal.getName() != null) {
                return principal.getName();
            }
        } catch (Exception e) {
            log.debug("Could not extract user ID from request", e);
        }
        return null;
    }

    /**
     * Extract client IP address from request headers.
     * Checks X-Forwarded-For (proxy) before falling back to remote address.
     */
    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) throws ServletException {
        // Apply to all requests
        return false;
    }
}
