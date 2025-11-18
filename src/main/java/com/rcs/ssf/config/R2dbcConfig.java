package com.rcs.ssf.config;

import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactories;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Conditional R2DBC ConnectionFactory auto-creation when a R2DBC URL is present.
 *
 * This configuration is intentionally conservative: it only creates a ConnectionFactory
 * bean if one is missing and `spring.r2dbc.url` is present. If you rely on Spring
 * Boot's own auto-configuration, this bean will be skipped.
 *
 * NOTE: Ensure the proper R2DBC driver dependency and `spring.r2dbc.*` properties are
 * present in your runtime environment (e.g., in application.yml/properties) when using
 * reactive repositories.
 */
@Configuration
@ConditionalOnProperty(prefix = "spring.r2dbc", name = "url")
public class R2dbcConfig {

    @Bean
    @ConditionalOnMissingBean(ConnectionFactory.class)
    public ConnectionFactory connectionFactory(@Value("${spring.r2dbc.url}") String r2dbcUrl) {
        // Let ConnectionFactories parse the driver + URL; this works if the driver supports the scheme.
        return ConnectionFactories.get(r2dbcUrl);
    }
}
