package com.rcs.ssf.config;

import com.rcs.ssf.security.GraphQLAuthorizationInstrumentation;
import graphql.scalars.ExtendedScalars;
import io.micrometer.core.instrument.Counter;
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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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

        private final Counter graphqlResponsesSuccess;
        private final Counter graphqlResponsesError;
        private final Map<String, Counter> operationCounters;

        public MetricsInterceptor(MeterRegistry meterRegistry) {
            this.meterRegistry = meterRegistry;
            this.graphqlResponsesSuccess = meterRegistry.counter("graphql_responses_total", "status", "success");
            this.graphqlResponsesError = meterRegistry.counter("graphql_responses_total", "status", "error");
            this.operationCounters = new ConcurrentHashMap<>();
            // Pre-populate operation counters for expected operation types
            operationCounters.put("query", meterRegistry.counter("graphql_requests_total", "operation", "query"));
            operationCounters.put("mutation", meterRegistry.counter("graphql_requests_total", "operation", "mutation"));
            operationCounters.put("subscription", meterRegistry.counter("graphql_requests_total", "operation", "subscription"));
            operationCounters.put("unknown", meterRegistry.counter("graphql_requests_total", "operation", "unknown"));
        }

        @Override
        public @NonNull Mono<WebGraphQlResponse> intercept(@NonNull WebGraphQlRequest request, @NonNull Chain chain) {
            // Log GraphQL requests if needed
            String query = request.getDocument();
            if (query != null && !query.isEmpty()) {
                logger.debug("GraphQL query: {}", query);
            }

            // Track GraphQL request metrics
            String opType = extractOperationType(query);
            operationCounters.get(opType).increment();

            return chain.next(request)
                    .doOnNext(response -> {
                        // Track response status
                        if (response.getErrors().isEmpty()) {
                            graphqlResponsesSuccess.increment();
                        } else {
                            graphqlResponsesError.increment();
                        }
                    });
        }

        private String extractOperationType(String query) {
            if (query == null || query.trim().isEmpty()) {
                return "unknown";
            }
            // Extract the first word from the query, ignoring whitespace and comments
            // GraphQL queries start with operation type (query, mutation, subscription) optionally followed by name
            String firstWord = query.trim().split("\\s+")[0].toLowerCase();
            switch (firstWord) {
                case "query":
                    return "query";
                case "mutation":
                    return "mutation";
                case "subscription":
                    return "subscription";
                default:
                    return "unknown";
            }
        }
    }
}
