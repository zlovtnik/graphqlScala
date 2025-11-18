package com.rcs.ssf.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;
import java.util.Objects;

/**
 * DataSource configuration for Oracle database connectivity.*
 * This configuration provides explicit DataSource bean creation only when Spring Boot's
 * autoconfiguration has not already created a DataSource. In most cases, Spring Boot's
 * DataSourceAutoConfiguration will create the DataSource from spring.datasource.* properties
 * in application.yml/properties, making this bean unnecessary.
 * This bean is retained for:
 * 1. Legacy deployments that disable DataSourceAutoConfiguration
 * 2. Custom initialization/pool settings beyond standard spring.datasource properties
 * 3. Explicit control when multiple DataSource instances are required
 * To use Spring Boot's autoconfiguration exclusively, remove this class entirely and rely on
 * spring.datasource.* properties in application configuration.
 */
@Configuration
@ConditionalOnProperty(prefix = "app.datasource", name = "enabled", havingValue = "true", matchIfMissing = false)
public class DataSourceConfig {

    /**
     * Creates a primary DataSource bean only if Spring Boot's autoconfiguration
     * has not already created one.
     * Validation:
     * - Ensures spring.datasource.url is present and non-blank (required for connection)
     * - Ensures spring.datasource.username is present and non-blank (required for authentication)
     * - Ensures spring.datasource.password is present and non-blank (required for authentication)
     *     * JDBC 4+ auto-detects the driver from the URL, so driverClassName is omitted to avoid
     * redundancy and allow the JDBC runtime to select the appropriate driver.
     *     * Fails fast at application startup with clear error messages if any required property is missing.
     * 
     * @param url Database connection URL (required, non-blank)
     * @param username Database username (required, non-blank)
     * @param password Database password (required, non-blank)
     * @return DataSource configured and validated from application properties
     * @throws IllegalStateException if any required property is missing or blank
     */
    @Bean
    @Primary
    @ConditionalOnMissingBean(DataSource.class)
    public DataSource dataSource(
        @Value("${spring.datasource.url}") String url,
        @Value("${spring.datasource.username}") String username,
        @Value("${spring.datasource.password}") String password
    ) {
        // Validate required properties at startup to fail fast with actionable errors
        url = Objects.requireNonNull(url, 
            "Missing required property 'spring.datasource.url'. " +
            "Configure database connection URL in application.yml/properties");
        username = Objects.requireNonNull(username,
            "Missing required property 'spring.datasource.username'. " +
            "Configure database username in application.yml/properties");
        password = Objects.requireNonNull(password,
            "Missing required property 'spring.datasource.password'. " +
            "Configure database password in application.yml/properties");
        
        // Validate non-blank (allow ObjectProvider to handle required=false cases)
        if (url.isBlank()) {
            throw new IllegalStateException(
                "Property 'spring.datasource.url' is blank. " +
                "Configure a valid database connection URL in application.yml/properties");
        }
        if (username.isBlank()) {
            throw new IllegalStateException(
                "Property 'spring.datasource.username' is blank. " +
                "Configure a valid database username in application.yml/properties");
        }
        if (password.isBlank()) {
            throw new IllegalStateException(
                "Property 'spring.datasource.password' is blank. " +
                "Configure a valid database password in application.yml/properties");
        }
        
        return DataSourceBuilder.create()
            .url(url)
            .username(username)
            .password(password)
            // Driver class omitted: JDBC 4+ auto-detects driver from URL
            // Allows runtime to select appropriate driver without redundant configuration
            .build();
    }
}
