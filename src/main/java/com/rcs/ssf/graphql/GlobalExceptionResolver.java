package com.rcs.ssf.graphql;

import graphql.GraphQLError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.dao.DataAccessException;
import org.springframework.graphql.execution.DataFetcherExceptionResolver;
import reactor.core.publisher.Mono;

import jakarta.validation.ValidationException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Global exception resolver for GraphQL errors.
 * 
 * This resolver intercepts exceptions during field resolution and maps them
 * to GraphQL errors. In development mode, it exposes real exception messages.
 * In production, it returns generic error messages.
 */
@Configuration
public class GlobalExceptionResolver {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionResolver.class);

    @Bean
    public DataFetcherExceptionResolver exceptionResolver(Environment environment) {
        return (exception, environment1) -> {
            // Detect if we're in development mode
            boolean isDevelopment = isDevelopmentEnvironment(environment);
            
            logger.error("GraphQL DataFetcher Exception Resolved: {}", exception.getMessage(), exception);
            
            // Build error message based on environment
            String message = isDevelopment 
                ? (exception.getMessage() != null ? exception.getMessage() : exception.toString())
                : buildGenericMessage(exception);
            
            // Create GraphQL error
            GraphQLError graphQLError = GraphQLError.newError()
                .message(message)
                .build();
            
            return Mono.just(Collections.singletonList(graphQLError));
        };
    }
    
    /**
     * Determines if the application is running in a development environment.
     * Returns true if no profiles are active or if any of the active profiles
     * match "dev", "development", "local", or "test" (case-insensitive).
     *
     * @param environment the Spring environment to check
     * @return true if in development mode, false otherwise
     */
    private static boolean isDevelopmentEnvironment(Environment environment) {
        String[] activeProfiles = environment.getActiveProfiles();
        return activeProfiles.length == 0 || 
            Arrays.stream(activeProfiles)
                .anyMatch(p -> p.equalsIgnoreCase("dev") || 
                             p.equalsIgnoreCase("development") || 
                             p.equalsIgnoreCase("local") || 
                             p.equalsIgnoreCase("test"));
    }
    
    private static String buildGenericMessage(Throwable exception) {
        if (exception instanceof IllegalArgumentException) {
            return "Invalid input provided";
        } else if (exception instanceof SecurityException) {
            return "Access denied";
        } else if (exception instanceof DataAccessException) {
            return "Data access error occurred";
        } else if (exception instanceof ValidationException) {
            return "Validation failed for the request";
        } else {
            return "An error occurred while processing your request";
        }
    }
}
