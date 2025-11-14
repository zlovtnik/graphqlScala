package com.rcs.ssf.security;

import org.springframework.graphql.server.WebGraphQlInterceptor;
import org.springframework.graphql.server.WebGraphQlRequest;
import org.springframework.graphql.server.WebGraphQlResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * GraphQL interceptor that enforces authentication for GraphQL queries and mutations
 * at the GraphQL operation level (before data fetchers are executed).
 *
 * This complements the {@link JwtAuthenticationFilter} servlet filter by:
 * 1. Allowing public endpoints like /graphiql
 * 2. Allowing introspection queries without authentication (to enable development/tooling)
 * 3. Enforcing authentication for all other GraphQL operations (queries/mutations)
 * 4. Providing early rejection before data fetcher execution
 *
 * The authentication check happens AFTER the servlet filter has a chance to
 * populate SecurityContext with a valid JWT token.
 */
@Component
public class GraphQLAuthorizationInstrumentation implements WebGraphQlInterceptor {

    @Override
    public @NonNull Mono<WebGraphQlResponse> intercept(@NonNull WebGraphQlRequest request, @NonNull Chain chain) {
        // Allow introspection queries without authentication
        if (isIntrospectionQuery(request)) {
            return chain.next(request);
        }

        // Enforce authentication for non-introspection GraphQL operations
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        // Check if user is authenticated
        if (authentication == null ||
                !authentication.isAuthenticated() ||
                authentication instanceof AnonymousAuthenticationToken) {
            return Mono.error(new AccessDeniedException(
                    "Authentication required: Missing or invalid JWT token. " +
                    "Please provide a valid JWT token in the Authorization header."));
        }

        // Continue with the chain
        return chain.next(request);
    }

    /**
     * Determines if the GraphQL request is an introspection query.
     * Introspection queries are allowed without authentication to support
     * development tools and IDE features.
     *
     * @param request the GraphQL request
     * @return true if the request is an introspection query, false otherwise
     */
    private boolean isIntrospectionQuery(@NonNull WebGraphQlRequest request) {
        String document = request.getDocument();
        
        // If no document is present, it's not an introspection query
        if (document == null || document.isBlank()) {
            return false;
        }
        
        // Normalize whitespace and convert to lowercase for case-insensitive matching
        String normalizedDocument = document.trim().toLowerCase();
        
        // Check for common introspection query indicators
        return normalizedDocument.contains("__schema") ||
               normalizedDocument.contains("__type") ||
               normalizedDocument.startsWith("query introspectionquery") ||
               normalizedDocument.contains("introspectionquery");
    }
}
