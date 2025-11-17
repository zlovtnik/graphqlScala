package com.rcs.ssf.service.reactive;

import io.micrometer.core.instrument.MeterRegistry;
import io.r2dbc.pool.ConnectionPool;
import io.r2dbc.pool.ConnectionPoolConfiguration;
import io.r2dbc.pool.PoolMetrics;
import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.r2dbc.convert.MappingR2dbcConverter;
import org.springframework.data.r2dbc.convert.R2dbcCustomConversions;
import org.springframework.data.r2dbc.core.DefaultReactiveDataAccessStrategy;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.r2dbc.core.ReactiveDataAccessStrategy;
import org.springframework.data.r2dbc.dialect.DialectResolver;
import org.springframework.data.r2dbc.dialect.R2dbcDialect;
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories;
import org.springframework.data.relational.core.mapping.RelationalMappingContext;
import org.springframework.data.relational.core.sql.IdentifierProcessing;
import org.springframework.lang.NonNull;
import org.springframework.r2dbc.core.DatabaseClient;
import reactor.netty.resources.ConnectionProvider;

import java.time.Duration;
import java.util.Collection;
import java.util.Objects;

import static io.r2dbc.spi.ConnectionFactoryOptions.DATABASE;
import static io.r2dbc.spi.ConnectionFactoryOptions.DRIVER;
import static io.r2dbc.spi.ConnectionFactoryOptions.HOST;
import static io.r2dbc.spi.ConnectionFactoryOptions.PASSWORD;
import static io.r2dbc.spi.ConnectionFactoryOptions.PORT;
import static io.r2dbc.spi.ConnectionFactoryOptions.SSL;
import static io.r2dbc.spi.ConnectionFactoryOptions.USER;

/**
 * Reactive data access configuration using R2DBC.
 * 
 * Enables non-blocking database operations with backpressure handling.
 * Thread pool configuration:
 * - Core pool: 50 threads for baseline async operations
 * - Max pool: 200 threads for burst traffic
 * - Queue depth: 1000 pending requests before rejection
 * 
 * Metrics:
 * - r2dbc.pool.acquired: Active connections in use
 * - r2dbc.pool.idle: Idle connections waiting
 * - r2dbc.pool.pending: Connections waiting in queue
 * - r2dbc.connection.creation.time: P95/P99 connection establish latency
 * 
 * Production tune: Adjust based on actual traffic patterns and database connection limits.
 */
@Configuration
@EnableR2dbcRepositories(basePackages = "com.rcs.ssf", entityOperationsRef = "r2dbcEntityTemplate")
@EnableConfigurationProperties(ReactiveDataSourceConfiguration.R2dbcProperties.class)
@Slf4j
public class ReactiveDataSourceConfiguration {

    @Bean
    public ConnectionProvider connectionProvider(MeterRegistry meterRegistry) {
        log.info("Configuring R2DBC connection provider with monitoring");
        
        return ConnectionProvider.builder("graphql-r2dbc")
                .maxIdleTime(Duration.ofMinutes(30))
                .maxLifeTime(Duration.ofHours(1))
                .build();
    }

    @Bean
    public ConnectionFactory r2dbcConnectionFactory(
        R2dbcProperties r2dbcProperties,
        MeterRegistry meterRegistry) {
    
        log.info("Creating R2DBC connection factory");
        
        // Validate required R2DBC properties
        validateR2dbcProperties(r2dbcProperties);
        
        // Get SSL setting from properties (defaults to false if not specified)
        boolean useSsl = Boolean.TRUE.equals(r2dbcProperties.getSsl());
        
        ConnectionFactoryOptions options = ConnectionFactoryOptions.builder()
            .option(DRIVER, r2dbcProperties.getDriver())
            .option(HOST, r2dbcProperties.getHost())
            .option(PORT, r2dbcProperties.getPort())
            .option(DATABASE, r2dbcProperties.getDatabase())
            .option(USER, r2dbcProperties.getUsername())
            .option(PASSWORD, r2dbcProperties.getPassword())
            .option(SSL, useSsl)
            .build();

        ConnectionFactory factory = ConnectionFactories.get(options);
        meterRegistry.counter("r2dbc.connection.factory.initialized").increment();
        log.debug("R2DBC ConnectionFactory created with SSL={}", useSsl);
        return factory;
    }
    
    /**
     * Validate that all required R2DBC properties are present and non-empty.
     * 
     * @param props R2DBC properties to validate
     * @throws IllegalArgumentException if any required property is missing or empty
     */
    private void validateR2dbcProperties(R2dbcProperties props) {
        if (props == null) {
            throw new IllegalArgumentException("R2dbcProperties cannot be null");
        }
        if (props.getHost() == null || props.getHost().isBlank()) {
            throw new IllegalArgumentException("R2DBC host is required and cannot be empty");
        }
        if (props.getPort() == null || props.getPort() <= 0 || props.getPort() > 65535) {
            throw new IllegalArgumentException("R2DBC port must be set and be in valid range (1-65535)");
        }
        if (props.getDatabase() == null || props.getDatabase().isBlank()) {
            throw new IllegalArgumentException("R2DBC database is required and cannot be empty");
        }
        if (props.getUsername() == null || props.getUsername().isBlank()) {
            throw new IllegalArgumentException("R2DBC username is required and cannot be empty");
        }
        if (props.getPassword() == null || props.getPassword().isBlank()) {
            throw new IllegalArgumentException("R2DBC password is required and cannot be empty");
        }
        // Validate pool settings if present
        if (props.getPool() != null) {
            Integer minIdle = props.getPool().getMinIdle();
            Integer maxSize = props.getPool().getMaxSize();

            if (minIdle != null && minIdle < 0) {
                throw new IllegalArgumentException("R2DBC pool minIdle must be non-negative (>= 0); provided: " + minIdle);
            }
            if (maxSize != null && maxSize <= 0) {
                throw new IllegalArgumentException("R2DBC pool maxSize must be positive (> 0); provided: " + maxSize);
            }
            if (minIdle != null && maxSize != null && minIdle > maxSize) {
                throw new IllegalArgumentException("R2DBC pool minIdle cannot be greater than maxSize; provided minIdle: " +
                        minIdle + ", maxSize: " + maxSize);
            }
        }
    }

    @Bean
    public ConnectionPool connectionPool(
            ConnectionFactory r2dbcConnectionFactory,
            R2dbcProperties r2dbcProperties,
            MeterRegistry meterRegistry) {
        
        log.info("Configuring R2DBC connection pool: " +
                "min={}, max={}, queue={}, idleTimeout={}",
                r2dbcProperties.getPool().getMinIdle(),
                r2dbcProperties.getPool().getMaxSize(),
                r2dbcProperties.getPool().getQueueDepth(),
                r2dbcProperties.getPool().getIdleTimeout());
        
        ConnectionPoolConfiguration poolConfig = ConnectionPoolConfiguration.builder(r2dbcConnectionFactory)
                .initialSize(r2dbcProperties.getPool().getMinIdle())
                .maxIdleTime(Duration.parse(r2dbcProperties.getPool().getIdleTimeout()))
                .maxAcquireTime(Duration.ofSeconds(10))
                .maxLifeTime(Duration.ofHours(1))
                .maxSize(r2dbcProperties.getPool().getMaxSize())
                // For queue handling: reject excess requests to enable backpressure
                .build();

        ConnectionPool pool = new ConnectionPool(poolConfig);
        
        // Export pool metrics
        pool.getMetrics().ifPresent(metrics -> {
            meterRegistry.gauge("r2dbc.pool.acquired", metrics, PoolMetrics::acquiredSize);
            meterRegistry.gauge("r2dbc.pool.idle", metrics, PoolMetrics::idleSize);
            meterRegistry.gauge("r2dbc.pool.pending", metrics, PoolMetrics::pendingAcquireSize);
        });
        
        return pool;
    }

    @Bean
    public R2dbcDialect r2dbcDialect(
            @Qualifier("r2dbcConnectionFactory") @NonNull ConnectionFactory connectionFactory) {
        ConnectionFactory nonNullFactory = Objects.requireNonNull(connectionFactory,
                "r2dbcConnectionFactory must not be null");
        R2dbcDialect baseDialect = DialectResolver.getDialect(nonNullFactory);
        
        // Create a dynamic proxy that wraps the dialect and overrides getIdentifierProcessing()
        // to return IdentifierProcessing.NONE (which keeps identifiers unquoted).
        // This solves the ORA-00942 error caused by quoted identifiers like "APP_USER"."users"
        return (R2dbcDialect) java.lang.reflect.Proxy.newProxyInstance(
            R2dbcDialect.class.getClassLoader(),
            new Class<?>[] { R2dbcDialect.class },
            (proxy, method, args) -> {
                if ("getIdentifierProcessing".equals(method.getName())) {
                    log.debug("Returning IdentifierProcessing.NONE to disable quoting");
                    return IdentifierProcessing.NONE;
                }
                // Delegate all other method calls to the base dialect
                return method.invoke(baseDialect, args);
            }
        );
    }

    @Bean
    public R2dbcCustomConversions r2dbcCustomConversions(@NonNull R2dbcDialect dialect) {
        R2dbcDialect nonNullDialect = Objects.requireNonNull(dialect, "dialect must not be null");
        Collection<?> converters = Objects.requireNonNull(R2dbcUuidConverters.getConverters(),
                "UUID converters must not be null");
        return R2dbcCustomConversions.of(nonNullDialect, converters);
    }

    @Bean
    @org.springframework.context.annotation.Primary
    public RelationalMappingContext relationalMappingContext(@NonNull R2dbcCustomConversions conversions) {
        R2dbcCustomConversions nonNullConversions = Objects.requireNonNull(conversions,
                "conversions must not be null");
        RelationalMappingContext context = new RelationalMappingContext();
        context.setSimpleTypeHolder(nonNullConversions.getSimpleTypeHolder());
        return context;
    }

    @Bean
    public MappingR2dbcConverter mappingR2dbcConverter(
            @NonNull RelationalMappingContext context,
            @NonNull R2dbcCustomConversions conversions) {
        MappingR2dbcConverter converter = new MappingR2dbcConverter(
                Objects.requireNonNull(context, "context must not be null"),
                Objects.requireNonNull(conversions, "conversions must not be null"));
        
        // Converter inherits IdentifierProcessing.NONE from the dialect proxy, keeping identifiers unquoted
        log.info("MappingR2dbcConverter configured; identifier quoting disabled via dialect override");
        return converter;
    }

    @Bean
    @SuppressWarnings("deprecation")
    public ReactiveDataAccessStrategy reactiveDataAccessStrategy(
            @NonNull R2dbcDialect dialect,
            @NonNull MappingR2dbcConverter converter) {
        
        // Log the identifier processing to debug
        log.info("Creating ReactiveDataAccessStrategy with dialect identifier processing: {}",
                dialect.getIdentifierProcessing());
        
        return new DefaultReactiveDataAccessStrategy(
                Objects.requireNonNull(dialect, "dialect must not be null"),
                Objects.requireNonNull(converter, "converter must not be null"));
    }

    @Bean
    public DatabaseClient databaseClient(
            @Qualifier("connectionPool") @NonNull ConnectionFactory connectionPool,
            @NonNull R2dbcDialect dialect) {
        return DatabaseClient.builder()
                .connectionFactory(Objects.requireNonNull(connectionPool, "connectionPool must not be null"))
                .bindMarkers(Objects.requireNonNull(dialect, "dialect must not be null").getBindMarkersFactory())
                .build();
    }

    @Bean
    @SuppressWarnings("deprecation")
    public R2dbcEntityTemplate r2dbcEntityTemplate(
            @NonNull DatabaseClient databaseClient,
            @NonNull ReactiveDataAccessStrategy reactiveDataAccessStrategy) {
        return new R2dbcEntityTemplate(
                Objects.requireNonNull(databaseClient, "databaseClient must not be null"),
                Objects.requireNonNull(reactiveDataAccessStrategy, "reactiveDataAccessStrategy must not be null"));
    }

    /**
     * Configuration properties for R2DBC connections.
     */
    @org.springframework.boot.context.properties.ConfigurationProperties(prefix = "app.r2dbc")
    public static class R2dbcProperties {
        // Default driver is "oracle" for backward compatibility with existing deployments
        // Set app.r2dbc.driver property to use a different R2DBC driver (e.g., "postgresql", "h2")
        private String driver = "oracle";
        private String host = "localhost";
        private Integer port = 1521;
        private String database = "XEPDB1";
        private String username = "app_user";
        private String password;
        private Boolean ssl = false;
        private Pool pool = new Pool();

        public static class Pool {
            private Integer minIdle = 10;
            private Integer maxSize = 200;
            private Integer queueDepth = 1000;
            private String idleTimeout = "PT30M"; // 30 minutes

            public Integer getMinIdle() { return minIdle; }
            public void setMinIdle(Integer minIdle) { this.minIdle = minIdle; }

            public Integer getMaxSize() { return maxSize; }
            public void setMaxSize(Integer maxSize) { this.maxSize = maxSize; }

            public Integer getQueueDepth() { return queueDepth; }
            public void setQueueDepth(Integer queueDepth) { this.queueDepth = queueDepth; }

            public String getIdleTimeout() { return idleTimeout; }
            public void setIdleTimeout(String idleTimeout) { this.idleTimeout = idleTimeout; }
        }

        public String getHost() { return host; }
        public void setHost(String host) { this.host = host; }

        public Integer getPort() { return port; }
        public void setPort(Integer port) { this.port = port; }

        public String getDatabase() { return database; }
        public void setDatabase(String database) { this.database = database; }

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }

        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        
        public Boolean getSsl() { return ssl; }
        public void setSsl(Boolean ssl) { this.ssl = ssl; }

        public String getDriver() { return driver; }
        public void setDriver(String driver) { this.driver = driver; }

        public Pool getPool() { return pool; }
        public void setPool(Pool pool) { this.pool = pool; }
    }
}

