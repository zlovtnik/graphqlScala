package com.rcs.ssf.security;

import com.rcs.ssf.util.HashUtils;
import graphql.language.Document;
import graphql.language.Field;
import graphql.language.OperationDefinition;
import graphql.language.Selection;
import graphql.language.SelectionSet;
import graphql.parser.InvalidSyntaxException;
import graphql.parser.Parser;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
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
import java.util.stream.Collectors;

/**
 * GraphQL interceptor that enforces authentication for GraphQL queries and
 * mutations
 * at the GraphQL operation level (before data fetchers are executed).
 *
 * This complements the {@link JwtAuthenticationFilter} servlet filter by:
 * 1. Allowing public endpoints like /graphiql
 * 2. Allowing introspection queries without authentication (to enable
 * development/tooling)
 * 3. Enforcing authentication for all other GraphQL operations
 * (queries/mutations)
 * 4. Providing early rejection before data fetcher execution
 *
 * The authentication check happens AFTER the servlet filter has a chance to
 * populate SecurityContext with a valid JWT token.
 */
@Component
@Slf4j
public class GraphQLAuthorizationInstrumentation implements WebGraphQlInterceptor {

    private static final Set<String> PUBLIC_MUTATIONS = Set.of("login", "logout", "createUser");
    private static final Set<String> PUBLIC_MUTATIONS_NORMALIZED = PUBLIC_MUTATIONS.stream()
            .map(name -> name.toLowerCase(Locale.ROOT))
            .collect(Collectors.toUnmodifiableSet());
    private static final Set<String> PUBLIC_SUBSCRIPTIONS = Set.of("dashboardStats");
    private static final Set<String> PUBLIC_SUBSCRIPTIONS_NORMALIZED = PUBLIC_SUBSCRIPTIONS.stream()
            .map(name -> name.toLowerCase(Locale.ROOT))
            .collect(Collectors.toUnmodifiableSet());
    private static final int PUBLIC_MUTATION_CACHE_MAX_ENTRIES = 512;
    private static final int PUBLIC_SUBSCRIPTION_CACHE_MAX_ENTRIES = 512;
    private final Cache<String, Boolean> publicMutationCache = Caffeine.newBuilder()
            .maximumSize(PUBLIC_MUTATION_CACHE_MAX_ENTRIES)
            .build();
    private final Cache<String, Boolean> publicSubscriptionCache = Caffeine.newBuilder()
            .maximumSize(PUBLIC_SUBSCRIPTION_CACHE_MAX_ENTRIES)
            .build();

    @Override
    public @NonNull Mono<WebGraphQlResponse> intercept(@NonNull WebGraphQlRequest request, @NonNull Chain chain) {
        // Allow introspection queries without authentication
        if (isIntrospectionQuery(request)) {
            return chain.next(request);
        }

        // Allow login and logout mutations without authentication
        if (isPublicMutation(request)) {
            return chain.next(request);
        }

        // Allow public subscriptions without authentication
        if (isPublicSubscription(request)) {
            return chain.next(request);
        }

        // Enforce authentication for non-introspection GraphQL operations
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        // Check if user is authenticated
        if (authentication == null ||
                !authentication.isAuthenticated() ||
                authentication instanceof AnonymousAuthenticationToken) {
            return Mono.<WebGraphQlResponse>error(new AccessDeniedException(
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
        String normalizedDocument = document.trim().toLowerCase(Locale.ROOT);

        // Check for common introspection query indicators
        return normalizedDocument.contains("__schema") ||
                normalizedDocument.contains("__type") ||
                normalizedDocument.startsWith("query introspectionquery") ||
                normalizedDocument.contains("introspectionquery");
    }

    /**
     * Determines if the GraphQL request is a public mutation (e.g., login,
     * registration).
     * Public mutations are allowed without authentication.
     *
     * @param request the GraphQL request
     * @return true if the request is a public mutation, false otherwise
     */
    private boolean isPublicMutation(@NonNull WebGraphQlRequest request) {
        String document = request.getDocument();
        String operationName = request.getOperationName();

        // If no document is present, it's not a public mutation
        if (document == null || document.isBlank()) {
            return false;
        }

        // Cheap textual pre-filter: skip parsing if no "mutation" token exists
        String normalizedDocument = document.toLowerCase(Locale.ROOT);
        if (!normalizedDocument.contains("mutation")) {
            return false;
        }

        // Use normalized document as cache key for stable lookups
        String cacheKey = generateCacheKey(document, operationName);
        if (cacheKey != null) {
            Boolean cachedResult = publicMutationCache.getIfPresent(cacheKey);
            if (cachedResult != null) {
                return cachedResult;
            }
        }

        // Parse AST to confirm it's a public mutation
        boolean parserResult = parseDocumentForPublicMutation(document, operationName);
        if (cacheKey != null) {
            publicMutationCache.put(cacheKey, parserResult);
        }
        return parserResult;
    }

    /**
     * Determines if the GraphQL request is a public subscription.
     * Public subscriptions are allowed without authentication.
     *
     * @param request the GraphQL request
     * @return true if the request is a public subscription, false otherwise
     */
    private boolean isPublicSubscription(@NonNull WebGraphQlRequest request) {
        String document = request.getDocument();
        String operationName = request.getOperationName();

        // If no document is present, it's not a public subscription
        if (document == null || document.isBlank()) {
            return false;
        }

        // Cheap textual pre-filter: skip parsing if no "subscription" token exists
        String normalizedDocument = document.toLowerCase(Locale.ROOT);
        if (!normalizedDocument.contains("subscription")) {
            return false;
        }

        // Use cached result if available
        String cacheKey = generateCacheKey(document, operationName);
        if (cacheKey != null) {
            Boolean cachedResult = publicSubscriptionCache.getIfPresent(cacheKey);
            if (cachedResult != null) {
                return cachedResult;
            }
        }

        // Parse AST to confirm it's a public subscription
        boolean parseResult = parseDocumentForPublicSubscription(document, operationName);
        if (cacheKey != null) {
            publicSubscriptionCache.put(cacheKey, parseResult);
        }
        return parseResult;
    }

    private boolean parseDocumentForPublicSubscription(String document, String operationName) {
        return parseDocumentForPublicOperation(
            document, 
            operationName, 
            OperationDefinition.Operation.SUBSCRIPTION, 
            PUBLIC_SUBSCRIPTIONS_NORMALIZED
        );
    }

    private boolean parseDocumentForPublicMutation(String document, String operationName) {
        return parseDocumentForPublicOperation(
            document, 
            operationName, 
            OperationDefinition.Operation.MUTATION, 
            PUBLIC_MUTATIONS_NORMALIZED
        );
    }

    /**
     * Generate a stable cache key from the GraphQL document and operation name.
     * Uses SHA-256 hash to create a compact, stable key.
     *
     * @param document      the GraphQL document
     * @param operationName the operation name (may be null)
     * @return hex-encoded SHA-256 hash of the document and operation name
     */
    private String generateCacheKey(String document, String operationName) {
        try {
            String key = operationName != null ? document + "|" + operationName : document;
            return HashUtils.sha256Hex(key);
        } catch (RuntimeException e) {
            log.warn("Error generating cache key, skipping cache for this document: {}", e.getMessage(), e);
            return null; // signal skip caching
        }
    }

    /**
     * Parses a GraphQL document and checks if all operations of the given type are public.
     * Shared implementation for both mutations and subscriptions.
     *
     * @param document       the GraphQL document
     * @param operationName  the operation name (may be null)
     * @param operationType  the operation type to check (MUTATION or SUBSCRIPTION)
     * @param allowedFields  the set of allowed field names (public mutations or subscriptions)
     * @return true if the document contains only public operations of the given type
     */
    private boolean parseDocumentForPublicOperation(
            String document,
            String operationName,
            OperationDefinition.Operation operationType,
            Set<String> allowedFields) {
        try {
            // Instantiate new Parser for each parse operation (thread-safe, no shared state)
            Parser parser = new Parser();
            Document parsedDocument = parser.parseDocument(document);

            // Count operations of the given type
            int operationCount = 0;
            for (var definition : parsedDocument.getDefinitions()) {
                if (definition instanceof OperationDefinition operation &&
                        operation.getOperation() == operationType) {
                    operationCount++;
                }
            }

            // If operationName is null and there are multiple operations of this type, reject
            if (operationName == null && operationCount > 1) {
                return false;
            }

            // Check each operation of the given type
            for (var definition : parsedDocument.getDefinitions()) {
                if (definition instanceof OperationDefinition operation &&
                        operation.getOperation() == operationType) {
                    // If operationName is specified, only check the matching operation
                    String opName = operation.getName();
                    if (operationName != null && !operationName.equals(opName)) {
                        continue; // skip non-matching operations
                    }

                    SelectionSet selectionSet = operation.getSelectionSet();
                    if (selectionSet == null) {
                        continue;
                    }
                    boolean sawAnyField = false;
                    for (Selection<?> selection : selectionSet.getSelections()) {
                        if (selection instanceof Field field) {
                            sawAnyField = true;
                            String fieldName = field.getName();
                            if (fieldName == null
                                    || !allowedFields.contains(fieldName.toLowerCase(Locale.ROOT))) {
                                return false; // mixed or non-public -> deny
                            }
                        } else {
                            return false; // non-field selection -> treat as non-public
                        }
                    }
                    if (sawAnyField) {
                        return true; // every field was public
                    }
                }
            }
        } catch (InvalidSyntaxException syntaxEx) {
            log.error("Invalid GraphQL syntax while checking for public operations", syntaxEx);
            return false;
        } catch (RuntimeException unexpected) {
            log.warn(
                    "Unexpected error while parsing GraphQL document for public operation detection, treating as non-public",
                    unexpected);
            return false;
        }
        return false;
    }
}
