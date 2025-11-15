package com.rcs.ssf.bootstrap;

import javax.sql.DataSource;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the Oracle schema bootstrapper so that the application can recover when
 * a fresh database volume is detected.
 */
@Configuration
@EnableConfigurationProperties(SchemaBootstrapProperties.class)
public class SchemaBootstrapConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "schema.bootstrap", name = "enabled", havingValue = "true", matchIfMissing = false)
    OracleSchemaBootstrapInitializer oracleSchemaBootstrapInitializer(DataSource dataSource,
            SchemaBootstrapProperties properties) {
        return new OracleSchemaBootstrapInitializer(dataSource, properties);
    }
}
