package com.rcs.ssf.config;

import com.rcs.ssf.security.GraphQLAuthorizationInstrumentation;
import graphql.scalars.ExtendedScalars;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.graphql.execution.RuntimeWiringConfigurer;
import org.springframework.graphql.server.WebGraphQlInterceptor;
import org.springframework.graphql.server.WebGraphQlRequest;
import org.springframework.graphql.server.WebGraphQlResponse;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * GraphQL Configuration for Spring Boot GraphQL.
 *
 * Registers GraphQL interceptors to handle authentication and other cross-cutting concerns.
 * The {@link GraphQLAuthorizationInstrumentation} is automatically registered as a bean
 * and will intercept all GraphQL requests to enforce JWT authentication.
 *
 * Also configures custom scalars including Long scalar to properly handle ID fields that
 * use Long type in Java but ID type in GraphQL schema.
 */
@Configuration
public class GraphQLConfig {

    /**
     * Configure RuntimeWiring to register custom scalars.
     *
     * Registers the Long scalar from graphql-java-extended-scalars to handle
     * coercion between GraphQL ID (String) and Java Long types.
     */
    @Bean
    public RuntimeWiringConfigurer runtimeWiringConfigurer() {
        return wiringBuilder -> wiringBuilder
                .scalar(ExtendedScalars.GraphQLLong);
    }

    /**
     * GraphQL interceptor that handles other cross-cutting concerns if needed.
     * The main authentication enforcement is handled by GraphQLAuthorizationInstrumentation.
     */
    @Component
    public static class GraphQLLoggingInterceptor implements WebGraphQlInterceptor {

        private static final Logger logger = LoggerFactory.getLogger(GraphQLLoggingInterceptor.class);

        @Override
        public @NonNull Mono<WebGraphQlResponse> intercept(@NonNull WebGraphQlRequest request, @NonNull Chain chain) {
            // Log GraphQL requests if needed
            String query = request.getDocument();
            if (query != null && !query.isEmpty()) {
                logger.debug("GraphQL query: {}", query);
            }
            return chain.next(request);
        }
    }
}
