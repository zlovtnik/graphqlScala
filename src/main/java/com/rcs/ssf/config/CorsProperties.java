package com.rcs.ssf.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * CORS properties configuration.
 *
 * Binds application properties under {@code app.cors.*} to this bean.
 * This centralizes CORS configuration across all endpoints and ensures
 * only trusted origins are permitted.
 *
 * Example application.yml configuration:
 * <pre>
 * app:
 *   cors:
 *     allowed-origins: http://localhost:4200,https://app.example.com
 * </pre>
 *
 * Production: Set CORS_ALLOWED_ORIGINS environment variable with comma-separated
 * trusted origins (e.g., https://app.example.com,https://www.example.com).
 */
@Component
@ConfigurationProperties(prefix = "app.cors")
public class CorsProperties {

    /**
     * Comma-separated list of allowed origins.
     * Only these origins will be permitted to access the API.
     * In production, this should be configured via environment variable to restrict access.
     */
    private String allowedOrigins = "http://localhost:4200";

    public String getAllowedOrigins() {
        return allowedOrigins;
    }

    public void setAllowedOrigins(String allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }
}
