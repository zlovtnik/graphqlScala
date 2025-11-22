package com.rcs.ssf.http.filter;

import io.opentelemetry.api.trace.Span;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Principal;
import java.util.HexFormat;
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
 * - Hashes PII (userId, client_ip) by default using SHA-256 with a validated salt
 * 
 * PII Hashing:
 * - By default (trace.include-pii=false), userId and client_ip are hashed to protect privacy
 * - The hash salt must be provided via TRACE_PII_SALT environment variable or Spring properties
 * - Salt is validated at startup: must be non-empty, min 16 chars, with character variety
 * - For production, configure via secure secrets manager (AWS Secrets Manager, Vault, etc.)
 * - Set trace.include-pii=true to disable hashing and export raw PII (NOT recommended)
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

    private static final int MINIMUM_SALT_LENGTH = 16;
    private static final String SALT_VALIDATION_ERROR = "PII salt validation failed: %s";

    @Value("${trace.include-pii:false}")
    private boolean includePii;

    @Value("${trace.pii-salt:}")
    private String piiSalt;

    /**
     * Validates PII salt at component initialization.
     * Throws IllegalArgumentException if salt is weak or missing.
     * Called by Spring during bean creation.
     */
    @jakarta.annotation.PostConstruct
    public void validatePiiSalt() {
        if (!includePii) {
            // PII hashing is enabled; salt is required
            if (piiSalt == null || piiSalt.isEmpty()) {
                String errorMsg = String.format(SALT_VALIDATION_ERROR,
                    "TRACE_PII_SALT not provided. Set via environment variable (recommended) or trace.pii-salt property. " +
                    "Minimum 16 characters with character variety required. " +
                    "Use AWS Secrets Manager, HashiCorp Vault, or similar for production.");
                log.error(errorMsg);
                throw new IllegalArgumentException(errorMsg);
            }
            
            if (piiSalt.length() < MINIMUM_SALT_LENGTH) {
                String errorMsg = String.format(SALT_VALIDATION_ERROR,
                    String.format("Salt too short (%d chars). Minimum %d characters required.",
                        piiSalt.length(), MINIMUM_SALT_LENGTH));
                log.error(errorMsg);
                throw new IllegalArgumentException(errorMsg);
            }
            
            if (!hasCharacterVariety(piiSalt)) {
                String errorMsg = String.format(SALT_VALIDATION_ERROR,
                    "Salt lacks character variety. Use mix of uppercase, lowercase, digits, and special characters.");
                log.error(errorMsg);
                throw new IllegalArgumentException(errorMsg);
            }
            
            log.info("PII salt validated successfully (length: {})", piiSalt.length());
        }
    }

    /**
     * Checks if salt has sufficient character variety:
     * At least one uppercase, one lowercase, one digit, and one special character.
     */
    private boolean hasCharacterVariety(String salt) {
        boolean hasUppercase = salt.matches(".*[A-Z].*");
        boolean hasLowercase = salt.matches(".*[a-z].*");
        boolean hasDigit = salt.matches(".*\\d.*");
        boolean hasSpecial = salt.matches(".*[!@#$%^&*()_+\\-=\\[\\]{};:'\",.<>?/\\\\|`~].*");
        
        return hasUppercase && hasLowercase && hasDigit && hasSpecial;
    }

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
                
                // Handle userId attribute: hash if includePii is false, otherwise include raw value
                if (userId != null && !userId.isEmpty()) {
                    currentSpan.setAttribute("http.user.id", includePii ? userId : hashPii(userId));
                } else {
                    currentSpan.setAttribute("http.user.id", "anonymous");
                }
                
                currentSpan.setAttribute("http.method", request.getMethod());
                currentSpan.setAttribute("http.url", request.getRequestURI());
                currentSpan.setAttribute("http.scheme", request.getScheme());
                
                // Handle client_ip attribute: hash if includePii is false, otherwise include raw value
                String clientIp = getClientIp(request);
                currentSpan.setAttribute("http.client_ip", includePii ? clientIp : hashPii(clientIp));
            }

            // Compute display userId for logging: respect includePii setting to avoid leaking PII in logs
            String displayUserId = userId;
            if (!includePii && userId != null && !userId.isEmpty()) {
                displayUserId = hashPii(userId);
            } else if (userId == null || userId.isEmpty()) {
                displayUserId = "anonymous";
            }

            log.debug("Trace correlation: requestId={}, userId={}, path={}", requestId, displayUserId, request.getRequestURI());

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
     * Hash PII (personally identifiable information) using SHA-256.
     * Combines the input with an app-specific salt for one-way hashing.
     * Returns hashed value as hex string, or "redacted" on hash error.
     * 
     * Salt is validated at startup by validatePiiSalt(), so it should never be null/empty here.
     */
    private String hashPii(String value) {
        if (value == null || value.isEmpty()) {
            return "redacted";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String combined = value + piiSalt;
            byte[] hash = digest.digest(combined.getBytes());
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            log.warn("Failed to hash PII value: {}", e.getMessage());
            return "redacted";
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
