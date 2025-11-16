package com.rcs.ssf.security;

import graphql.language.Document;
import graphql.language.Field;
import graphql.language.OperationDefinition;
import graphql.language.Selection;
import graphql.language.SelectionSet;
import graphql.parser.InvalidSyntaxException;
import graphql.parser.Parser;
import lombok.extern.slf4j.Slf4j;
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

import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

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
@Slf4j
public class GraphQLAuthorizationInstrumentation implements WebGraphQlInterceptor {

    private static final Set<String> PUBLIC_MUTATIONS = Set.of("login", "logout", "createUser");
    private static final Set<String> PUBLIC_MUTATIONS_NORMALIZED =
            PUBLIC_MUTATIONS.stream()
                    .map(name -> name.toLowerCase(Locale.ROOT))
                    .collect(Collectors.toUnmodifiableSet());
    private static final Parser GRAPHQL_PARSER = new Parser();
    private static final int PUBLIC_MUTATION_CACHE_MAX_ENTRIES = 512;
    private final ConcurrentHashMap<Integer, Boolean> publicMutationCache = new ConcurrentHashMap<>();

    @Override
    @SuppressWarnings("null")
    public @NonNull Mono<WebGraphQlResponse> intercept(@NonNull WebGraphQlRequest request, @NonNull Chain chain) {
        // Allow introspection queries without authentication
        if (isIntrospectionQuery(request)) {
            return chain.next(request);
        }

        // Allow login and logout mutations without authentication
        if (isPublicMutation(request)) {
            return chain.next(request);
        }

        // Enforce authentication for non-introspection GraphQL operations
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        // Check if user is authenticated
        if (authentication == null ||
                !authentication.isAuthenticated() ||
                authentication instanceof AnonymousAuthenticationToken) {
            @SuppressWarnings("null")
            Mono<WebGraphQlResponse> errorMono = Mono.error(new AccessDeniedException(
                "Authentication required: Missing or invalid JWT token. " +
                "Please provide a valid JWT token in the Authorization header."));
            return errorMono;
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
        String normalizedDocument = document.trim().toLowerCase(Locale.ROOT);
        
        // Check for common introspection query indicators
        return normalizedDocument.contains("__schema") ||
               normalizedDocument.contains("__type") ||
               normalizedDocument.startsWith("query introspectionquery") ||
               normalizedDocument.contains("introspectionquery");
    }

    /**
     * Determines if the GraphQL request is a public mutation (e.g., login, registration).
     * Public mutations are allowed without authentication.
     *
     * @param request the GraphQL request
     * @return true if the request is a public mutation, false otherwise
     */
    private boolean isPublicMutation(@NonNull WebGraphQlRequest request) {
        String document = request.getDocument();

        // If no document is present, it's not a public mutation
        if (document == null || document.isBlank()) {
            return false;
        }

        int cacheKey = document.hashCode();
        Boolean cachedResult = publicMutationCache.get(cacheKey);
        if (cachedResult != null) {
            return cachedResult;
        }

        String normalizedDocument = document.toLowerCase(Locale.ROOT);
        if (!normalizedDocument.contains("mutation")) {
            cachePublicMutationResult(cacheKey, false);
            return false;
        }

        for (String mutation : PUBLIC_MUTATIONS_NORMALIZED) {
            if (normalizedDocument.contains(mutation)) {
                cachePublicMutationResult(cacheKey, true);
                return true;
            }
        }

        boolean parserResult = parseDocumentForPublicMutation(document);
        cachePublicMutationResult(cacheKey, parserResult);
        return parserResult;
    }

    private boolean parseDocumentForPublicMutation(String document) {
        try {
            Document parsedDocument = GRAPHQL_PARSER.parseDocument(document);
            for (var definition : parsedDocument.getDefinitions()) {
                if (definition instanceof OperationDefinition operation &&
                        operation.getOperation() == OperationDefinition.Operation.MUTATION) {
                    SelectionSet selectionSet = operation.getSelectionSet();
                    if (selectionSet == null) {
                        continue;
                    }
                    for (Selection<?> selection : selectionSet.getSelections()) {
                        if (selection instanceof Field field) {
                            String fieldName = field.getName();
                            if (fieldName != null && PUBLIC_MUTATIONS_NORMALIZED.contains(fieldName.toLowerCase(Locale.ROOT))) {
                                return true;
                            }
                        }
                    }
                }
            }
        } catch (InvalidSyntaxException syntaxEx) {
            log.error("Invalid GraphQL syntax while checking for public mutations", syntaxEx);
            return false;
        } catch (RuntimeException unexpected) {
            log.warn("Unexpected error while parsing GraphQL document for public mutation detection", unexpected);
            throw unexpected;
        }
        return false;
    }

    private void cachePublicMutationResult(int cacheKey, boolean result) {
        if (publicMutationCache.size() >= PUBLIC_MUTATION_CACHE_MAX_ENTRIES) {
            publicMutationCache.clear();
        }
        publicMutationCache.put(cacheKey, result);
    }
}
