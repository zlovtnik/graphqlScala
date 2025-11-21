package com.rcs.ssf.http;

import com.rcs.ssf.service.ETagCacheService;
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

/**
 * HTTP filter for implementing ETag-based caching for read-only endpoints.
 * 
 * Enables HTTP conditional requests (If-None-Match) to reduce bandwidth
 * and improve performance for read operations.
 * 
 * ETags are cached in Redis with a 5-minute TTL to avoid expensive SHA-256
 * computation on every request. If Redis is unavailable, ETags are computed
 * on-the-fly without caching.
 * 
 * Configuration:
 * - ssf.cache.default-max-age: Default max-age in seconds for Cache-Control
 * header (default: 3600)
 * 
 * Controllers can override the Cache-Control header by setting it in the
 * response before the response is committed; if a Cache-Control header is already
 * present, this filter will not override it.
 */
@Component
@Slf4j
public class ETagFilter extends OncePerRequestFilter {

    private static final String ETAG_HEADER = "ETag";
    private static final String IF_NONE_MATCH_HEADER = "If-None-Match";
    private static final String CACHE_CONTROL_HEADER = "Cache-Control";
    private static final int NOT_MODIFIED = 304;
    private static final int DEFAULT_MAX_AGE_SECONDS = 3600;

    private final ETagCacheService etagCacheService;

    @Value("${ssf.cache.default-max-age:" + DEFAULT_MAX_AGE_SECONDS + "}")
    private int defaultMaxAgeSeconds;

    public ETagFilter(ETagCacheService etagCacheService) {
        this.etagCacheService = etagCacheService;
    }

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
                // Get ETag from cache (or compute and cache if not present)
                String etag = etagCacheService.getOrComputeETag(responseBody);

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

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) throws ServletException {
        // Skip ETag processing for GraphQL endpoints (they handle caching differently)
        String path = request.getRequestURI();
        return path.equals("/graphql") || path.startsWith("/graphql/");
    }
}
