package com.rcs.ssf.tracing;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Tracer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenTelemetry Configuration for distributed tracing.
 * 
 * NOTE: Spring Boot 3.5.7 provides automatic OpenTelemetry configuration via
 * spring-boot-starter-actuator when OTEL dependencies are present. This bean
 * ensures we have access to the Tracer for injection.
 * 
 * Spring Boot automatically configures:
 * - OpenTelemetry SDK with OTLP exporter via OpenTelemetryEnvironmentPostProcessor
 * - Resource attributes from spring.application.name and environment
 * - Span processor and exporter from OTEL_EXPORTER_OTLP_ENDPOINT
 * 
 * Environment Variables (auto-configured by Spring Boot):
 * - OTEL_EXPORTER_OTLP_ENDPOINT: Endpoint for Jaeger/Tempo (default: http://localhost:4317)
 * - OTEL_SERVICE_NAME: Application service name (spring.application.name used if unset)
 * - SPRING_APPLICATION_NAME: Sets the service name (must be set in properties)
 * 
 * Configuration in application.yml:
 * - spring.application.name: ssf
 * - otel.exporter.otlp.endpoint (environment variable preferred)
 */
@Configuration
@Slf4j
public class OtelConfig {

    @Value("${spring.application.name:ssf-graphql}")
    private String serviceName;

    /**
     * Provide the global Tracer bean for injection into components.
     * 
     * The OpenTelemetry SDK is auto-configured by Spring Boot 3.5.7+.
     * This bean makes the Tracer available for dependency injection.
     * 
     * Usage in services:
     * @Autowired
     * private Tracer tracer;
     * 
     * @param openTelemetry Global OpenTelemetry instance (auto-configured by Spring Boot)
     * @return Tracer for creating spans
     */
    @Bean
    public Tracer tracer(OpenTelemetry openTelemetry) {
        Tracer tracer = openTelemetry.getTracer(serviceName);
        log.info("OpenTelemetry Tracer initialized for service: {}", serviceName);
        return tracer;
    }

    /**
     * Factory method for MFAOperationInstrumentation AOP bean.
     * 
     * Creates the aspect that automatically instruments WebAuthn and MFA service calls.
     * Uses default slow-operation threshold of 2000ms.
     * 
     * @param tracer OpenTelemetry tracer for span creation
     * @return MFAOperationInstrumentation AOP aspect
     */
    @Bean
    public MFAOperationInstrumentation mfaOperationInstrumentation(Tracer tracer) {
        log.info("MFAOperationInstrumentation bean created with default threshold (2000ms)");
        return new MFAOperationInstrumentation(tracer);
    }

    /**
     * Factory method for DatabaseOperationInstrumentation AOP bean.
     * 
     * Creates the aspect that automatically instruments repository method calls.
     * Uses default slow-query threshold of 1000ms.
     * 
     * @param tracer OpenTelemetry tracer for span creation
     * @return DatabaseOperationInstrumentation AOP aspect
     */
    @Bean
    public DatabaseOperationInstrumentation databaseOperationInstrumentation(Tracer tracer) {
        log.info("DatabaseOperationInstrumentation bean created with default threshold (1000ms)");
        return new DatabaseOperationInstrumentation(tracer);
    }
}
