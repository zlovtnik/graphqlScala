package com.rcs.ssf.security;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.lang.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Content Security Policy (CSP) filter that generates nonces for inline scripts and styles.
 *
 * This filter:
 * 1. Generates a cryptographically secure nonce for each request
 * 2. Sets strict CSP headers without 'unsafe-inline' (requires nonces for inline code)
 * 3. Makes the nonce available as a request attribute for template rendering
 *
 * Nonce format: Base64-encoded 16 random bytes (~22 characters)
 *
 * CSP Directives:
 * - default-src 'self': Only allow same-origin resources
 * - script-src 'self' 'nonce-*': Allow same-origin scripts and nonce-protected inline scripts
 * - style-src 'self' 'nonce-*': Allow same-origin styles and nonce-protected inline styles
 * - img-src 'self' data:: Allow same-origin and data: URI images
 * - font-src 'self': Allow same-origin fonts
 * - connect-src 'self': Allow same-origin connections (XHR, fetch, WebSocket)
 *
 * @see <a href="https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Content-Security-Policy">MDN: Content-Security-Policy</a>
 */
public class CspHeaderFilter extends OncePerRequestFilter implements Ordered {

    private static final String NONCE_ATTRIBUTE = "cspNonce";
    private static volatile SecureRandom secureRandom;
    private static final int NONCE_BYTES = 16;

    @Value("${csp.script-hosts:https://cdn.jsdelivr.net}")
    private String scriptHosts;

    @Value("${csp.style-hosts:https://fonts.googleapis.com}")
    private String styleHosts;

    @Value("${csp.font-hosts:https://fonts.gstatic.com}")
    private String fontHosts;

    @Value("${csp.trusted-cdn-hosts:}")
    private String trustedCdnHosts;
    
    @Value("${csp.api-hosts:}")
    private String apiHosts;
    
    @Value("${csp.realtime-hosts:}")
    private String realtimeHosts;

    /**
     * Validates CSP configuration sources after dependency injection.
     * Runs validation on all configured hosts before the filter starts processing requests.
     */
    @PostConstruct
    public void validateConfiguration() {
        validateCspSource(trustedCdnHosts, "csp.trusted-cdn-hosts");
        validateCspSource(apiHosts, "csp.api-hosts");
        validateCspSource(realtimeHosts, "csp.realtime-hosts");
    }

    /**
     * Validates a CSP source string against security rules.
     * 
     * Allows:
     * - Null or blank values (skipped)
     * - Quoted keywords: 'self', 'none', 'unsafe-inline', 'unsafe-eval'
     * - HTTPS host sources optionally with port (e.g., https://example.com, https://example.com:8443)
     *
     * Rejects any strings containing semicolons or other CSP directive separators.
     *
     * @param source the CSP source string to validate
     * @param propertyName the configuration property name (for error messages)
     * @throws IllegalArgumentException if the source fails validation
     */
    private void validateCspSource(String source, String propertyName) {
        if (source == null || source.isBlank()) {
            return; // Null/blank values are acceptable
        }

        // CSP keyword pattern: 'keyword' format
        java.util.regex.Pattern cspKeywordPattern = java.util.regex.Pattern.compile("^'(self|none|unsafe-inline|unsafe-eval)'$");
        
        // HTTPS host pattern: https://host[:port] with optional a single left-most wildcard (e.g., https://*.example.com)
        // Note: Port validation is done separately below to ensure ports are in range 1-65535
        java.util.regex.Pattern httpsHostPattern = java.util.regex.Pattern.compile(
            "^https://((\\*\\.)?[a-z0-9-]+(?:\\.[a-z0-9-]+)*)(?::([0-9]+))?$",
            java.util.regex.Pattern.CASE_INSENSITIVE);

        for (String token : source.split("\\s+")) {
            if (token.isEmpty()) continue;
            
            // Reject if contains semicolon (directive separator)
            if (token.contains(";")) {
                throw new IllegalArgumentException(
                    String.format("Invalid CSP source in %s: contains semicolon (directive separator): %s", propertyName, token));
            }
            
            // Check if it's a valid keyword or HTTPS host
            if (cspKeywordPattern.matcher(token).matches()) {
                // Valid keyword, proceed
                continue;
            }
            
            java.util.regex.Matcher hostMatcher = httpsHostPattern.matcher(token);
            if (hostMatcher.matches()) {
                // Valid HTTPS host; validate port if present
                String portStr = hostMatcher.group(2);
                if (portStr != null && !portStr.isEmpty()) {
                    try {
                        int port = Integer.parseInt(portStr);
                        if (port < 1 || port > 65535) {
                            throw new IllegalArgumentException(
                                String.format("Invalid CSP source in %s: port must be in range 1-65535 (got %d): %s", 
                                propertyName, port, token));
                        }
                    } catch (NumberFormatException ex) {
                        throw new IllegalArgumentException(
                            String.format("Invalid CSP source in %s: invalid port number: %s", propertyName, token), ex);
                    }
                }
                continue;
            }
            
            throw new IllegalArgumentException(
                String.format("Invalid CSP source in %s: must be a quoted keyword ('self', 'none', 'unsafe-inline', 'unsafe-eval') or HTTPS host (optionally with port 1-65535): %s", 
                propertyName, token));
        }
    }

    /**
     * Lazy initialization of SecureRandom to avoid blocking application startup.
     * Uses double-checked locking for thread-safe lazy initialization.
     *
     * @return initialized SecureRandom instance
     */
    private static SecureRandom getSecureRandom() {
        if (secureRandom == null) {
            synchronized (CspHeaderFilter.class) {
                if (secureRandom == null) {
                    secureRandom = new SecureRandom();
                }
            }
        }
        return secureRandom;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        // Generate a cryptographically secure nonce for this request
        String nonce = generateNonce();

        // Store nonce as request attribute for template engines to use
        request.setAttribute(NONCE_ATTRIBUTE, nonce);

        // Build CSP header with configurable trusted domains
        String cspHeader = buildCspHeader(nonce);

        response.setHeader("Content-Security-Policy", cspHeader);

        // Also set report-only header to catch violations without blocking (optional, for monitoring)
        // Uncomment to enable CSP violation reporting:
        // response.setHeader("Content-Security-Policy-Report-Only", cspHeader + "; report-uri /api/csp-report");

        // Additional security headers
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-Frame-Options", "DENY");
        response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
        response.setHeader("Permissions-Policy", "geolocation=(), microphone=(), camera=()");

        filterChain.doFilter(request, response);
    }

    /**
     * Builds the Content-Security-Policy header value with configurable trusted domains.
     *
     * @param nonce The request-specific nonce for inline scripts/styles
     * @return The complete CSP header value
     */
    private String buildCspHeader(String nonce) {
        StringBuilder csp = new StringBuilder();
        csp.append("default-src 'self'; ");
        csp.append(String.format("script-src 'self' 'nonce-%s' %s; ", nonce, scriptHosts));
        csp.append(String.format("style-src 'self' 'nonce-%s' %s; ", nonce, styleHosts));

        // Build img-src with optional trusted CDN hosts
        csp.append("img-src 'self' data:");
        if (trustedCdnHosts != null && !trustedCdnHosts.isBlank()) {
            csp.append(" ").append(trustedCdnHosts);
        }
        csp.append("; ");
        
        // Build font-src with optional additional hosts
        csp.append(String.format("font-src 'self' %s; ", fontHosts));

        // Build connect-src with optional API and realtime hosts
        csp.append("connect-src 'self'");
        if (apiHosts != null && !apiHosts.isBlank()) {
            csp.append(" ").append(apiHosts);
        }
        if (realtimeHosts != null && !realtimeHosts.isBlank()) {
            csp.append(" ").append(realtimeHosts);
        }
        csp.append("; ");
        
        csp.append("frame-ancestors 'none'; ");
        csp.append("base-uri 'self'; ");
        csp.append("form-action 'self'");
        
        return csp.toString();
    }

    /**
     * Generates a cryptographically secure nonce (Number Used Once).
     *
     * @return Base64-encoded random nonce (~22 characters)
     */
    private String generateNonce() {
        byte[] nonceBytes = new byte[NONCE_BYTES];
        getSecureRandom().nextBytes(nonceBytes);
        return Base64.getEncoder().encodeToString(nonceBytes);
    }

    /**
     * Gets the nonce attribute name used to store/retrieve the nonce from request attributes.
     *
     * @return the nonce attribute name
     */
    public static String getNonceAttribute() {
        return NONCE_ATTRIBUTE;
    }

    @Override
    public int getOrder() {
        return 0;
    }
}
