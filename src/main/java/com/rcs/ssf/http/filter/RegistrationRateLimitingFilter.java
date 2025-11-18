package com.rcs.ssf.http.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple IP-based rate limiter for the user registration endpoint.
 *
 * Ensures that automated registration attempts cannot flood the system
 * by enforcing a small sliding window (per remote IP) across POST /api/users.
 */
@Component
@Slf4j
public class RegistrationRateLimitingFilter extends OncePerRequestFilter implements Ordered {

    private static final int MAX_REQUESTS_PER_WINDOW = 5; // allow 5 registrations / IP / minute
    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final Map<String, RequestBucket> buckets = new ConcurrentHashMap<>();
    private final Clock clock;

    public RegistrationRateLimitingFilter() {
        this(Clock.systemUTC());
    }

    RegistrationRateLimitingFilter(Clock clock) {
        this.clock = clock;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10; // Run before GraphQL logging filter
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {
        String clientKey = resolveClientKey(request);
        long nowMillis = clock.millis();

        RateLimitDecision decision = evaluateRequest(clientKey, nowMillis);
        setRateLimitHeaders(response, decision, nowMillis);

        if (!decision.allowed()) {
            log.warn("Registration rate limit exceeded for key {}", clientKey);
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(Math.max(1, decision.retryAfterSeconds())));
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter()
                    .write("{\"error\":\"RATE_LIMITED\",\"message\":\"Too many registration attempts. Please try again later.\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private RateLimitDecision evaluateRequest(String key, long nowMillis) {
        RequestBucket bucket = buckets.computeIfAbsent(key, ignored -> new RequestBucket(nowMillis, 0));
        synchronized (bucket) {
            long windowStart = bucket.windowStartMillis;
            if (nowMillis - windowStart >= WINDOW.toMillis()) {
                bucket.windowStartMillis = nowMillis;
                bucket.requestCount = 0;
                windowStart = nowMillis;
            }

            if (bucket.requestCount < MAX_REQUESTS_PER_WINDOW) {
                bucket.requestCount++;
                long remaining = MAX_REQUESTS_PER_WINDOW - bucket.requestCount;
                long resetEpochSeconds = (bucket.windowStartMillis + WINDOW.toMillis()) / 1000;
                return new RateLimitDecision(true, remaining, resetEpochSeconds, 0);
            }

            long retryAfterMillis = (bucket.windowStartMillis + WINDOW.toMillis()) - nowMillis;
            long retryAfterSeconds = Math.max(1, (long) Math.ceil(retryAfterMillis / 1000.0));
            long resetEpochSeconds = (bucket.windowStartMillis + WINDOW.toMillis()) / 1000;
            return new RateLimitDecision(false, 0, resetEpochSeconds, retryAfterSeconds);
        }
    }

    private void setRateLimitHeaders(HttpServletResponse response, RateLimitDecision decision, long nowMillis) {
        response.setHeader("X-RateLimit-Limit", Integer.toString(MAX_REQUESTS_PER_WINDOW));
        response.setHeader("X-RateLimit-Remaining", Long.toString(Math.max(decision.remainingRequests(), 0)));
        long reset = decision.resetEpochSeconds();
        if (reset > 0) {
            response.setHeader("X-RateLimit-Reset", Long.toString(reset));
        } else {
            response.setHeader("X-RateLimit-Reset", Long.toString((nowMillis + WINDOW.toMillis()) / 1000));
        }
    }

    private String resolveClientKey(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private String normalizePath(HttpServletRequest request) {
        String path = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isBlank() && path.startsWith(contextPath)) {
            path = path.substring(contextPath.length());
        }
        return path;
    }

    private boolean isRegistrationAttempt(HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return false;
        }
        String path = normalizePath(request);
        return "/api/users".equals(path) || "/api/users/".equals(path);
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) throws ServletException {
        return !isRegistrationAttempt(request);
    }

    private static final class RequestBucket {
        private long windowStartMillis;
        private int requestCount;

        private RequestBucket(long windowStartMillis, int requestCount) {
            this.windowStartMillis = windowStartMillis;
            this.requestCount = requestCount;
        }
    }

    private record RateLimitDecision(boolean allowed, long remainingRequests, long resetEpochSeconds,
            long retryAfterSeconds) {
    }
}
