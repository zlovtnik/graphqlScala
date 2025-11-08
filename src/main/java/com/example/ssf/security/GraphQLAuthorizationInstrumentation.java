package com.example.ssf.security;

import org.springframework.graphql.server.WebGraphQlInterceptor;
import org.springframework.graphql.server.WebGraphQlRequest;
import org.springframework.graphql.server.WebGraphQlResponse;
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
 * 2. Enforcing authentication for all GraphQL operations (queries/mutations)
 * 3. Providing early rejection before data fetcher execution
 *
 * The authentication check happens AFTER the servlet filter has a chance to
 * populate SecurityContext with a valid JWT token.
 */
@Component
public class GraphQLAuthorizationInstrumentation implements WebGraphQlInterceptor {

    @Override
    public Mono<WebGraphQlResponse> intercept(WebGraphQlRequest request, Chain chain) {
        // Enforce authentication for GraphQL operations
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        // Check if user is authenticated
        if (authentication == null ||
                !authentication.isAuthenticated() ||
                authentication instanceof AnonymousAuthenticationToken) {
            throw new AccessDeniedException(
                    "Authentication required: Missing or invalid JWT token. " +
                    "Please provide a valid JWT token in the Authorization header.");
        }

        // Continue with the chain
        return chain.next(request);
    }
}

