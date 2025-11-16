package com.rcs.ssf.security;

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
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int NONCE_BYTES = 16;

    @Value("${csp.trusted-cdn-hosts:}")
    private String trustedCdnHosts;
    
    @Value("${csp.api-hosts:}")
    private String apiHosts;
    
    @Value("${csp.realtime-hosts:}")
    private String realtimeHosts;

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
        csp.append(String.format("script-src 'self' 'nonce-%s' https://cdn.jsdelivr.net; ", nonce));
        csp.append(String.format("style-src 'self' 'nonce-%s' https://fonts.googleapis.com; ", nonce));
        
        // Build img-src with optional trusted CDN hosts
        csp.append("img-src 'self' data:");
        if (trustedCdnHosts != null && !trustedCdnHosts.isBlank()) {
            csp.append(" ").append(trustedCdnHosts);
        }
        csp.append("; ");
        
        // Build font-src with optional additional hosts
        csp.append("font-src 'self' https://fonts.gstatic.com; ");
        
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
        RANDOM.nextBytes(nonceBytes);
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
