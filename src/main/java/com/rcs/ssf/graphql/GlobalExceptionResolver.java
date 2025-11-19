package com.rcs.ssf.graphql;

import graphql.GraphQLError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.graphql.execution.DataFetcherExceptionResolver;
import reactor.core.publisher.Mono;

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
            String[] activeProfiles = environment.getActiveProfiles();
            boolean isDevelopment = activeProfiles.length == 0 || 
                Arrays.stream(activeProfiles)
                    .anyMatch(p -> p.equalsIgnoreCase("dev") || 
                                 p.equalsIgnoreCase("development") || 
                                 p.equalsIgnoreCase("local") || 
                                 p.equalsIgnoreCase("test"));
            
            logger.error("GraphQL DataFetcher Exception Resolved: {}", exception.getMessage(), exception);
            
            // Build error message based on environment
            String message = isDevelopment 
                ? (exception.getMessage() != null ? exception.getMessage() : exception.toString())
                : buildGenericMessage(exception);
            
            // Create GraphQL error
            GraphQLError graphQLError = GraphQLError.newError()
                .message(message)
                .build();
            
            List<GraphQLError> errors = Collections.singletonList(graphQLError);
            @SuppressWarnings("unchecked")
            Mono<List<GraphQLError>> result = (Mono<List<GraphQLError>>) (Mono<?>) Mono.just(errors);
            return result;
        };
    }
    
    private static String buildGenericMessage(Throwable exception) {
        if (exception instanceof IllegalArgumentException) {
            return "Invalid input provided";
        } else if (exception instanceof SecurityException) {
            return "Access denied";
        } else {
            return "An error occurred while processing your request";
        }
    }
}
