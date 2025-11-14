package com.rcs.ssf.http;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/**
 * HTTP filter for implementing ETag-based caching for read-only endpoints.
 * 
 * Enables HTTP conditional requests (If-None-Match) to reduce bandwidth
 * and improve performance for read operations.
 * 
 * Configuration:
 * - ssf.cache.default-max-age: Default max-age in seconds for Cache-Control
 * header (default: 3600)
 * 
 * Controllers can override the Cache-Control header by setting it in the
 * response
 * before the response is committed; if a Cache-Control header is already
 * present,
 * this filter will not override it.
 */
@Component
@Slf4j
public class ETagFilter extends OncePerRequestFilter {

    private static final String ETAG_HEADER = "ETag";
    private static final String IF_NONE_MATCH_HEADER = "If-None-Match";
    private static final String CACHE_CONTROL_HEADER = "Cache-Control";
    private static final int NOT_MODIFIED = 304;
    private static final int DEFAULT_MAX_AGE_SECONDS = 3600;

    @Value("${ssf.cache.default-max-age:" + DEFAULT_MAX_AGE_SECONDS + "}")
    private int defaultMaxAgeSeconds;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        // Only apply ETag caching to GET and HEAD requests
        if ("GET".equalsIgnoreCase(request.getMethod()) || "HEAD".equalsIgnoreCase(request.getMethod())) {
            // Wrap response to capture content
            ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(response);

            filterChain.doFilter(request, wrappedResponse);

            // Process only successful responses (2xx)
            if (wrappedResponse.getStatus() >= 200 && wrappedResponse.getStatus() < 300) {
                byte[] responseBody = wrappedResponse.getContentAsByteArray();
                String etag = generateETag(responseBody);

                // Check If-None-Match header
                String ifNoneMatch = request.getHeader(IF_NONE_MATCH_HEADER);
                if (etag.equals(ifNoneMatch)) {
                    // Return 304 Not Modified
                    response.setStatus(NOT_MODIFIED);
                    response.setHeader(ETAG_HEADER, etag);
                    // Only set Cache-Control if not already present
                    if (response.getHeader(CACHE_CONTROL_HEADER) == null) {
                        response.setHeader(CACHE_CONTROL_HEADER, "max-age=" + defaultMaxAgeSeconds);
                    }
                    log.debug("ETag match detected for: {}, returning 304 Not Modified", request.getRequestURI());
                } else {
                    // Return response with ETag
                    wrappedResponse.setHeader(ETAG_HEADER, etag);
                    // Only set Cache-Control if not already present (allow controllers to override)
                    if (wrappedResponse.getHeader(CACHE_CONTROL_HEADER) == null) {
                        wrappedResponse.setHeader(CACHE_CONTROL_HEADER,
                                "max-age=" + defaultMaxAgeSeconds + ", must-revalidate");
                    }
                    wrappedResponse.copyBodyToResponse();
                    log.debug("Set ETag for: {} -> {}", request.getRequestURI(), etag);
                }
            } else {
                // For non-2xx responses, still need to copy content back
                wrappedResponse.copyBodyToResponse();
            }
        } else {
            // Non-GET requests bypass ETag caching
            filterChain.doFilter(request, response);
        }
    }

    /**
     * Generate ETag from response content using SHA-256 hash.
     */
    private String generateETag(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content);
            return "\"" + Base64.getEncoder().encodeToString(hash) + "\"";
        } catch (NoSuchAlgorithmException e) {
            log.error("SHA-256 not available, falling back to content array hash", e);
            // Use hashCode of content array as fallback (better than length alone)
            return "\"" + Integer.toHexString(java.util.Arrays.hashCode(content)) + "\"";
        }
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) throws ServletException {
        // Skip ETag processing for GraphQL endpoints (they handle caching differently)
        String path = request.getRequestURI();
        return path.equals("/graphql") || path.startsWith("/graphql/");
    }

}
