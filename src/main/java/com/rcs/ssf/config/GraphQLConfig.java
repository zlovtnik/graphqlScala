package com.rcs.ssf.config;

import com.rcs.ssf.security.GraphQLAuthorizationInstrumentation;
import graphql.scalars.ExtendedScalars;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.graphql.execution.RuntimeWiringConfigurer;
import org.springframework.graphql.server.WebGraphQlInterceptor;
import org.springframework.graphql.server.WebGraphQlRequest;
import org.springframework.graphql.server.WebGraphQlResponse;
import org.springframework.lang.NonNull;
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
     * Configure GraphQL source with custom instrumentation.
     * Note: Instrumentation beans are automatically registered by Spring GraphQL
     */

    /**
     * GraphQL interceptor for logging and metrics collection.
     */
    @Bean
    public WebGraphQlInterceptor metricsInterceptor(MeterRegistry meterRegistry) {
        return new MetricsInterceptor(meterRegistry);
    }

    /**
     * GraphQL interceptor for logging requests and collecting metrics.
     */
    public static class MetricsInterceptor implements WebGraphQlInterceptor {

        private static final Logger logger = LoggerFactory.getLogger(MetricsInterceptor.class);
        private final MeterRegistry meterRegistry;

        public MetricsInterceptor(MeterRegistry meterRegistry) {
            this.meterRegistry = meterRegistry;
        }

        @Override
        public @NonNull Mono<WebGraphQlResponse> intercept(@NonNull WebGraphQlRequest request, @NonNull Chain chain) {
            // Log GraphQL requests if needed
            String query = request.getDocument();
            if (query != null && !query.isEmpty()) {
                logger.debug("GraphQL query: {}", query);
            }

            // Track GraphQL request metrics
            meterRegistry.counter("graphql_requests_total", "operation", extractOperationType(query)).increment();

            return chain.next(request)
                    .doOnNext(response -> {
                        // Track response status
                        String status = response.getErrors().isEmpty() ? "success" : "error";
                        meterRegistry.counter("graphql_responses_total", "status", status).increment();
                    });
        }

        private String extractOperationType(String query) {
            if (query == null || query.trim().isEmpty()) {
                return "unknown";
            }
            String trimmed = query.trim().toLowerCase();
            if (trimmed.startsWith("query")) {
                return "query";
            } else if (trimmed.startsWith("mutation")) {
                return "mutation";
            } else if (trimmed.startsWith("subscription")) {
                return "subscription";
            }
            return "unknown";
        }
    }
}
