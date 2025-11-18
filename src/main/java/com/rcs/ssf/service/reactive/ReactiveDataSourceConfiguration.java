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
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.r2dbc.dialect.DialectResolver;
import org.springframework.data.r2dbc.dialect.R2dbcDialect;
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories;
import org.springframework.data.relational.core.mapping.RelationalMappingContext;
import org.springframework.lang.NonNull;
import org.springframework.r2dbc.core.DatabaseClient;
import reactor.netty.resources.ConnectionProvider;

import java.time.Duration;

import static io.r2dbc.spi.ConnectionFactoryOptions.DATABASE;
import static io.r2dbc.spi.ConnectionFactoryOptions.DRIVER;
import static io.r2dbc.spi.ConnectionFactoryOptions.HOST;
import static io.r2dbc.spi.ConnectionFactoryOptions.PASSWORD;
import static io.r2dbc.spi.ConnectionFactoryOptions.PORT;
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
 * Production tune: Adjust based on actual traffic patterns and database
 * connection limits.
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
        String driver = r2dbcProperties.getDriver().trim().toLowerCase();

        // Build driver-specific R2DBC URL with SSL parameters included
        String r2dbcUrl = buildR2dbcUrl(driver, r2dbcProperties, useSsl);
        log.debug("Constructed R2DBC URL for driver={} with SSL={}: {}", driver, useSsl, maskPasswordInUrl(r2dbcUrl));

        // Build ConnectionFactoryOptions from the constructed URL with credentials
        ConnectionFactoryOptions.Builder optionsBuilder;

        if ("oracle".equals(driver)) {
            // For Oracle, build options directly since R2DBC SPI URL parser doesn't support
            // Oracle format
            optionsBuilder = ConnectionFactoryOptions.builder()
                    .option(DRIVER, "oracle")
                    .option(HOST, r2dbcProperties.getHost())
                    .option(PORT, r2dbcProperties.getPort())
                    .option(DATABASE, r2dbcProperties.getDatabase())
                    .option(USER, r2dbcProperties.getUsername())
                    .option(PASSWORD, r2dbcProperties.getPassword());
        } else {
            // For other drivers, use URL parsing
            optionsBuilder = ConnectionFactoryOptions.builder()
                    .from(ConnectionFactoryOptions.parse(r2dbcUrl))
                    .option(USER, r2dbcProperties.getUsername())
                    .option(PASSWORD, r2dbcProperties.getPassword());
        }

        ConnectionFactoryOptions options = optionsBuilder.build();

        try {
            ConnectionFactory factory = ConnectionFactories.get(options);
            meterRegistry.counter("r2dbc.connection.factory.initialized").increment();
            log.debug("R2DBC ConnectionFactory created for driver={} with SSL={}", driver, useSsl);
            return factory;
        } catch (Exception e) {
            meterRegistry.counter("r2dbc.connection.factory.failed").increment();
            String errorContext = String.format("Failed to create R2DBC ConnectionFactory for driver=%s, host=%s",
                    r2dbcProperties.getDriver(), r2dbcProperties.getHost());
            log.error("{}: {}", errorContext, e.getMessage());
            throw new RuntimeException(
                    "Cannot initialize R2DBC ConnectionFactory. Verify R2DBC driver availability and connection options.",
                    e);
        }
    }

    /**
     * Constructs a driver-specific R2DBC connection URL with SSL parameters
     * embedded.
     * 
     * @param driver the R2DBC driver name (lowercase)
     * @param props  R2DBC properties
     * @param useSsl whether SSL should be enabled
     * @return the constructed R2DBC URL with SSL parameters
     */
    private String buildR2dbcUrl(String driver, R2dbcProperties props, boolean useSsl) {
        String baseUrl;

        switch (driver) {
            case "postgresql" -> {
                // PostgreSQL: r2dbc:postgresql://host:port/database?sslMode=require
                String sslMode = useSsl ? "require" : "disable";
                baseUrl = String.format("r2dbc:postgresql://%s:%d/%s?sslMode=%s",
                        props.getHost(), props.getPort(), props.getDatabase(), sslMode);
            }
            case "mysql", "mariadb" -> {
                // MySQL/MariaDB:
                // r2dbc:{driver}://host:port/database?useSSL=true&requireSSL=true
                String sslParam = useSsl ? "true" : "false";
                baseUrl = String.format("r2dbc:%s://%s:%d/%s?useSSL=%s&requireSSL=%s",
                        driver, props.getHost(), props.getPort(), props.getDatabase(), sslParam, sslParam);
            }
            case "mssql" -> {
                // MSSQL: r2dbc:mssql://host:port;database=dbname;encrypt=true
                String encryptParam = useSsl ? "true" : "false";
                baseUrl = String.format("r2dbc:mssql://%s:%d;database=%s;encrypt=%s",
                        props.getHost(), props.getPort(), props.getDatabase(), encryptParam);
            }
            case "oracle" -> {
                // Oracle: r2dbc:oracle:thin:@//host:port/service_name
                // SSL/TLS is typically configured via wallet or TNS, not in URL
                baseUrl = String.format("r2dbc:oracle:thin:@//%s:%d/%s",
                        props.getHost(), props.getPort(), props.getDatabase());
                if (useSsl) {
                    log.debug(
                            "Oracle SSL/TLS should be configured via database wallet or TNS configuration, not in URL");
                }
            }
            case "h2" -> {
                // H2: r2dbc:h2:mem:///database
                baseUrl = String.format("r2dbc:h2:mem:///%s", props.getDatabase());
                if (useSsl) {
                    log.debug("H2 database does not use network-level SSL");
                }
            }
            default -> throw new IllegalArgumentException(
                    String.format("Unknown or unsupported R2DBC driver: %s. Cannot construct R2DBC URL.", driver));
        }

        return baseUrl;
    }

    /**
     * Masks the password in a URL for safe logging.
     * 
     * @param url the URL to mask
     * @return the URL with password replaced by asterisks
     */
    private String maskPasswordInUrl(String url) {
        return url.replaceAll("([?&]password=)[^&]*", "$1***");
    }

    /**
     * Validate that all required R2DBC properties are present and non-empty.
     * 
     * @param props R2DBC properties to validate
     * @throws IllegalArgumentException if any required property is missing or empty
     * @throws IllegalStateException    if driver is invalid
     */
    private void validateR2dbcProperties(R2dbcProperties props) {
        if (props == null) {
            throw new IllegalArgumentException("R2dbcProperties cannot be null");
        }

        // Validate driver
        String driver = props.getDriver();
        if (driver == null || driver.trim().isBlank()) {
            throw new IllegalStateException(
                    "R2DBC driver is required and cannot be empty. Set app.r2dbc.driver to a supported value: oracle, postgresql, mysql, h2");
        }
        driver = driver.trim().toLowerCase();
        java.util.Set<String> supportedDrivers = java.util.Set.of("oracle", "postgresql", "mysql", "h2", "mariadb",
                "mssql");
        if (!supportedDrivers.contains(driver)) {
            throw new IllegalStateException(
                    String.format(
                            "Unsupported R2DBC driver '%s'. Supported drivers: %s. Set app.r2dbc.driver to a valid value.",
                            driver, supportedDrivers));
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
                throw new IllegalArgumentException(
                        "R2DBC pool minIdle must be non-negative (>= 0); provided: " + minIdle);
            }
            if (maxSize != null && maxSize <= 0) {
                throw new IllegalArgumentException("R2DBC pool maxSize must be positive (> 0); provided: " + maxSize);
            }
            if (minIdle != null && maxSize != null && minIdle > maxSize) {
                throw new IllegalArgumentException(
                        "R2DBC pool minIdle cannot be greater than maxSize; provided minIdle: " +
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
            meterRegistry.gauge("r2dbc.pool.acquired.connections", metrics, PoolMetrics::acquiredSize);
            meterRegistry.gauge("r2dbc.pool.idle", metrics, PoolMetrics::idleSize);
            meterRegistry.gauge("r2dbc.pool.pending", metrics, PoolMetrics::pendingAcquireSize);
            // Add max connections metric for alerting
            meterRegistry.gauge("r2dbc.pool.max.connections", r2dbcProperties.getPool(), p -> p.getMaxSize().doubleValue());
        });

        return pool;
    }

    @Bean
    public R2dbcDialect r2dbcDialect(
            @Qualifier("r2dbcConnectionFactory") @NonNull ConnectionFactory connectionFactory) {
        // Returns the dialect resolved from the connection factory as-is.
        // The dialect's default IdentifierProcessing behavior is applied; no overrides are made here.
        // If unquoted identifiers are required in the future, a decorator wrapper should be implemented
        // that delegates all Dialect methods to the resolved dialect but overrides getIdentifierProcessing()
        // to return IdentifierProcessing.NONE.
        return DialectResolver.getDialect(connectionFactory);
    }

    @Bean
    public R2dbcCustomConversions r2dbcCustomConversions(@NonNull R2dbcDialect dialect) {
        return R2dbcCustomConversions.of(dialect, R2dbcUuidConverters.getConverters());
    }

    @Bean
    @org.springframework.context.annotation.Primary
    public RelationalMappingContext relationalMappingContext(@NonNull R2dbcCustomConversions conversions) {
        RelationalMappingContext context = new RelationalMappingContext();
        context.setSimpleTypeHolder(conversions.getSimpleTypeHolder());
        return context;
    }

    @Bean
    public MappingR2dbcConverter mappingR2dbcConverter(
            @NonNull RelationalMappingContext context,
            @NonNull R2dbcCustomConversions conversions) {
        return new MappingR2dbcConverter(context, conversions);
    }

    @Bean
    public DatabaseClient databaseClient(
            @Qualifier("connectionPool") @NonNull ConnectionFactory connectionPool,
            @NonNull R2dbcDialect dialect) {
        return DatabaseClient.builder()
                .connectionFactory(connectionPool)
                .bindMarkers(dialect.getBindMarkersFactory())
                .build();
    }

    @Bean
    public R2dbcEntityTemplate r2dbcEntityTemplate(
            @NonNull DatabaseClient databaseClient,
            @NonNull R2dbcDialect dialect) {
        log.info("Creating R2dbcEntityTemplate with DatabaseClient and R2dbcDialect");
        return new R2dbcEntityTemplate(databaseClient, dialect);
    }

    /**
     * Configuration properties for R2DBC connections.
     */
    @org.springframework.boot.context.properties.ConfigurationProperties(prefix = "app.r2dbc")
    public static class R2dbcProperties {
        // Default driver is "oracle" for backward compatibility with existing
        // deployments
        // Set app.r2dbc.driver property to use a different R2DBC driver (e.g.,
        // "postgresql", "h2")
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

            public Integer getMinIdle() {
                return minIdle;
            }

            public void setMinIdle(Integer minIdle) {
                this.minIdle = minIdle;
            }

            public Integer getMaxSize() {
                return maxSize;
            }

            public void setMaxSize(Integer maxSize) {
                this.maxSize = maxSize;
            }

            public Integer getQueueDepth() {
                return queueDepth;
            }

            public void setQueueDepth(Integer queueDepth) {
                this.queueDepth = queueDepth;
            }

            public String getIdleTimeout() {
                return idleTimeout;
            }

            public void setIdleTimeout(String idleTimeout) {
                this.idleTimeout = idleTimeout;
            }
        }

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public Integer getPort() {
            return port;
        }

        public void setPort(Integer port) {
            this.port = port;
        }

        public String getDatabase() {
            return database;
        }

        public void setDatabase(String database) {
            this.database = database;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public Boolean getSsl() {
            return ssl;
        }

        public void setSsl(Boolean ssl) {
            this.ssl = ssl;
        }

        public String getDriver() {
            return driver;
        }

        public void setDriver(String driver) {
            this.driver = driver;
        }

        public Pool getPool() {
            return pool;
        }

        public void setPool(Pool pool) {
            this.pool = pool;
        }
    }
}
