package com.rcs.ssf.security;

import graphql.GraphQLError;
import graphql.execution.DataFetcherExceptionHandler;
import graphql.execution.DataFetcherExceptionHandlerParameters;
import graphql.execution.DataFetcherExceptionHandlerResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * GraphQL exception handler that exposes real error messages to facilitate debugging.
 * 
 * In development mode (profiles: dev, development, local, test), returns the full exception stack trace and message.
 * In production, returns a safe error message.
 * 
 * Authentication and authorization checks are performed earlier in the request
 * pipeline via {@link JwtAuthenticationFilter} and {@link GraphQLAuthorizationInstrumentation}.
 */
@Component
public class GraphQLSecurityHandler implements DataFetcherExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GraphQLSecurityHandler.class);
    private static final Set<String> DEV_PROFILES_SET = Collections.unmodifiableSet(
            Set.of("dev", "development", "local", "test"));

    private final Environment environment;

    /**
     * Constructor for dependency injection.
     * 
     * @param environment the Spring environment used to detect dev profiles
     */
    public GraphQLSecurityHandler(Environment environment) {
        this.environment = environment;
    }

    @Override
    public CompletableFuture<DataFetcherExceptionHandlerResult> handleException(
            DataFetcherExceptionHandlerParameters handlerParameters) {

        Throwable exception = handlerParameters.getException();
        boolean isDevelopment = isDevEnvironment();

        // Log the full exception for debugging
        logger.error("GraphQL exception in data fetcher", exception);

        // Translate Spring Security exceptions to GraphQL errors
        if (exception instanceof AccessDeniedException) {
            String message = isDevelopment ? exception.getMessage() : "Access Denied";
            GraphQLError error = GraphQLError.newError()
                    .message(message != null ? message : "Access Denied: Insufficient permissions for this operation")
                    .extensions(buildExtensions(exception, isDevelopment))
                    .build();
            return CompletableFuture.completedFuture(
                    DataFetcherExceptionHandlerResult.newResult().error(error).build());
        }

        if (exception instanceof AuthenticationException) {
            logger.warn("Authentication exception encountered during GraphQL data fetch", exception);
            String message = isDevelopment ? exception.getMessage() : "Authentication Failed";
            GraphQLError error = GraphQLError.newError()
                    .message(message != null ? message : "Authentication Failed")
                    .extensions(buildExtensions(exception, isDevelopment))
                    .build();
            return CompletableFuture.completedFuture(
                    DataFetcherExceptionHandlerResult.newResult().error(error).build());
        }

        // For other exceptions, expose the real error message in dev mode
        String message = isDevelopment && exception.getMessage() != null
                ? exception.getMessage()
                : "An error occurred";
        
        GraphQLError error = GraphQLError.newError()
                .message(message)
                .extensions(buildExtensions(exception, isDevelopment))
                .build();

        return CompletableFuture.completedFuture(
                DataFetcherExceptionHandlerResult.newResult().error(error).build());
    }

    private boolean isDevEnvironment() {
        if (environment == null) {
            return false;
        }
        String[] activeProfiles = environment.getActiveProfiles();
        if (activeProfiles.length == 0) {
            // Check default profiles if no active profiles are set
            activeProfiles = environment.getDefaultProfiles();
        }
        return Arrays.stream(activeProfiles)
                .anyMatch(profile -> DEV_PROFILES_SET.contains(profile.toLowerCase()));
    }

    private Map<String, Object> buildExtensions(Throwable exception, boolean isDevelopment) {
        Map<String, Object> extensions = new HashMap<>();
        extensions.put("exceptionType", exception.getClass().getSimpleName());
        
        if (isDevelopment) {
            // Include stack trace in development mode
            extensions.put("stackTrace", getStackTrace(exception));
            extensions.put("cause", exception.getCause() != null ? exception.getCause().toString() : null);
        }
        
        return extensions;
    }

    private String getStackTrace(Throwable exception) {
        StringBuilder sb = new StringBuilder();
        sb.append(exception.getClass().getName()).append(": ").append(exception.getMessage()).append("\n");
        
        for (StackTraceElement element : exception.getStackTrace()) {
            sb.append("\tat ").append(element).append("\n");
        }
        
        if (exception.getCause() != null) {
            sb.append("Caused by: ").append(getStackTrace(exception.getCause()));
        }
        
        return sb.toString();
    }
}
