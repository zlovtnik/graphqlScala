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
import com.rcs.ssf.util.EnvironmentDetectionUtils;
import java.util.Collections;

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
    @SuppressWarnings("null")
    public DataFetcherExceptionResolver exceptionResolver(Environment environment) {
        return (exception, dataFetchingEnv) -> {
            // Detect if we're in development mode using Spring Environment (for profile detection)
            // Note: dataFetchingEnv is the GraphQL DataFetchingEnvironment (unused in this resolver)
            boolean isDevelopment = EnvironmentDetectionUtils.isDevelopment(environment);
            
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
