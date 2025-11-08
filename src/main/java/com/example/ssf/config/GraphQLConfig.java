package com.example.ssf.config;

import com.example.ssf.security.GraphQLAuthorizationInstrumentation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.graphql.server.WebGraphQlInterceptor;
import org.springframework.graphql.server.WebGraphQlRequest;
import org.springframework.graphql.server.WebGraphQlResponse;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * GraphQL Configuration for Spring Boot GraphQL.
 *
 * Registers GraphQL interceptors to handle authentication and other cross-cutting concerns.
 * The {@link GraphQLAuthorizationInstrumentation} is automatically registered as a bean
 * and will intercept all GraphQL requests to enforce JWT authentication.
 */
@Configuration
public class GraphQLConfig {

    /**
     * GraphQL interceptor that handles other cross-cutting concerns if needed.
     * The main authentication enforcement is handled by GraphQLAuthorizationInstrumentation.
     */
    @Component
    public static class GraphQLLoggingInterceptor implements WebGraphQlInterceptor {

        private static final Logger logger = LoggerFactory.getLogger(GraphQLLoggingInterceptor.class);

        @Override
        public Mono<WebGraphQlResponse> intercept(WebGraphQlRequest request, Chain chain) {
            // Log GraphQL requests if needed
            String query = request.getDocument();
            if (query != null && !query.isEmpty()) {
                logger.debug("GraphQL query: {}", query);
            }
            return chain.next(request);
        }
    }
}

