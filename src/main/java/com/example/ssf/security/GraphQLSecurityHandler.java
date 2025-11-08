package com.example.ssf.security;

import graphql.GraphQLError;
import graphql.execution.DataFetcherExceptionHandler;
import graphql.execution.DataFetcherExceptionHandlerParameters;
import graphql.execution.DataFetcherExceptionHandlerResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/**
 * GraphQL-specific exception handler that translates Spring Security exceptions
 * into GraphQL errors. Authentication and authorization checks are performed
 * earlier in the request pipeline via {@link JwtAuthenticationFilter} and
 * {@link GraphQLAuthorizationInstrumentation}.
 *
 * This handler focuses ONLY on translating security exceptions to user-friendly
 * GraphQL error responses.
 */
@Component
public class GraphQLSecurityHandler implements DataFetcherExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GraphQLSecurityHandler.class);

    @Override
    public CompletableFuture<DataFetcherExceptionHandlerResult> handleException(
            DataFetcherExceptionHandlerParameters handlerParameters) {

        Throwable exception = handlerParameters.getException();

        // Translate Spring Security exceptions to GraphQL errors
        if (exception instanceof AccessDeniedException) {
            GraphQLError error = GraphQLError.newError()
                    .message("Access Denied: Insufficient permissions for this operation")
                    .build();
            return CompletableFuture.completedFuture(
                    DataFetcherExceptionHandlerResult.newResult().error(error).build());
        }

        if (exception instanceof AuthenticationException) {
            GraphQLError error = GraphQLError.newError()
                    .message("Authentication Failed: " + exception.getMessage())
                    .build();
            return CompletableFuture.completedFuture(
                    DataFetcherExceptionHandlerResult.newResult().error(error).build());
        }

        // Handle other exceptions with their original message
        logger.error("Unexpected error in GraphQL data fetching", exception);
        GraphQLError error = GraphQLError.newError()
                .message("An unexpected error occurred")
                .build();

        return CompletableFuture.completedFuture(
                DataFetcherExceptionHandlerResult.newResult().error(error).build());
    }
}
