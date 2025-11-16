package com.rcs.ssf.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.lang.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;

/**
 * Filter to log GraphQL requests for debugging malformed JSON issues.
 * Logs request body for POST requests to /graphql endpoint, redacting sensitive variables.
 */
public class GraphQLRequestLoggingFilter extends OncePerRequestFilter implements Ordered {

    private static final Logger logger = LoggerFactory.getLogger(GraphQLRequestLoggingFilter.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Value("${graphql.request.logging.enabled:false}")
    private boolean loggingEnabled;

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE; // Run before other filters
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        if (!loggingEnabled || !"POST".equalsIgnoreCase(request.getMethod()) || !"/graphql".equals(request.getRequestURI())) {
            filterChain.doFilter(request, response);
            return;
        }

        // Wrap the request to capture the body
        CachedBodyHttpServletRequest wrappedRequest = new CachedBodyHttpServletRequest(request);
        String body = wrappedRequest.getBody();

        if (body == null || body.trim().isEmpty()) {
            logger.warn("Received empty or null GraphQL request body from {}", request.getRemoteAddr());
        } else if (!isValidJson(body)) {
            logger.warn("Received malformed GraphQL request body from {}: {}", request.getRemoteAddr(), body);
        } else {
            // Log the request with sensitive data redacted
            String sanitizedBody = sanitizeGraphQLRequest(body);
            logger.debug("GraphQL request from {}: {}", request.getRemoteAddr(), sanitizedBody);
        }

        filterChain.doFilter(wrappedRequest, response);
    }

    /**
     * Sanitizes a GraphQL request by redacting the variables payload.
     *
     * @param body the GraphQL request body
     * @return sanitized request with variables redacted
     */
    private String sanitizeGraphQLRequest(String body) {
        try {
            JsonNode root = OBJECT_MAPPER.readTree(body);
            if (root instanceof ObjectNode objNode) {
                // Redact variables if present
                if (objNode.has("variables")) {
                    objNode.put("variables", "[REDACTED]");
                }
                return objNode.toString();
            }
        } catch (Exception e) {
            logger.debug("Unable to parse GraphQL body for sanitization", e);
        }
        return body;
    }

    private boolean isValidJson(String json) {
        if (json == null || json.trim().isEmpty()) {
            return false;
        }
        try {
            OBJECT_MAPPER.readTree(json);
            return true;
        } catch (IOException ex) {
            logger.debug("Invalid GraphQL JSON payload", ex);
            return false;
        }
    }
}