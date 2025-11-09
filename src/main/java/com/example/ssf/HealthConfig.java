package com.example.ssf;

import io.minio.MinioClient;
import io.minio.errors.MinioException;
import org.springframework.boot.actuate.health.CompositeHealthContributor;
import org.springframework.boot.actuate.health.HealthContributor;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;

import javax.sql.DataSource;
import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

@Configuration
@EnableConfigurationProperties(MinioProperties.class)
public class HealthConfig {
    private static final String DATABASE_FILE = "databaseFile";
    private static final String DATABASE_CONNECTION = "databaseConnection";
    private static final String MINIO = "minio";

    private final Environment env;
    private final MinioProperties minioProperties;
    private MinioClient minioClient;

    public HealthConfig(Environment env, MinioProperties minioProperties) {
        this.env = env;
        this.minioProperties = minioProperties;
        this.minioClient = MinioClient.builder()
                .endpoint(minioProperties.getUrl())
                .credentials(minioProperties.getAccessKey(), minioProperties.getSecretKey())
                .build();
    }

    // For testing
    void setMinioClient(MinioClient minioClient) {
        this.minioClient = minioClient;
    }

    @Bean
    @ConditionalOnBean(DataSource.class)
    public HealthContributor customHealthContributors(DataSource dataSource) {
        Map<String, HealthIndicator> indicators = new LinkedHashMap<>();
        indicators.put(DATABASE_FILE, databaseFileHealthIndicator());
        indicators.put(DATABASE_CONNECTION, databaseConnectionHealthIndicator(dataSource));
        indicators.put(MINIO, minioHealthIndicator());
        return CompositeHealthContributor.fromMap(indicators);
    }

    public HealthIndicator databaseFileHealthIndicator() {
        return () -> {
            String dbUrlLocal = env.getProperty("spring.datasource.url");
            if (dbUrlLocal != null && dbUrlLocal.startsWith("jdbc:h2:mem:")) {
                // H2 database, no file to check
                return org.springframework.boot.actuate.health.Health.up().withDetail(DATABASE_FILE, "in-memory").build();
            }
            String dbPath = dbUrlLocal != null ? dbUrlLocal.replace("jdbc:sqlite:", "") : "";
            File dbFile = new File(dbPath);
            if (dbFile.exists()) {
                return org.springframework.boot.actuate.health.Health.up().withDetail(DATABASE_FILE, "exists").build();
            } else {
                return org.springframework.boot.actuate.health.Health.down().withDetail(DATABASE_FILE, "not found").build();
            }
        };
    }

    public HealthIndicator databaseConnectionHealthIndicator(DataSource dataSource) {
        return () -> {
            try (Connection conn = dataSource.getConnection()) {
                if (conn.isValid(2)) {
                    return org.springframework.boot.actuate.health.Health.up().withDetail(DATABASE_CONNECTION, "valid").build();
                } else {
                    return org.springframework.boot.actuate.health.Health.down().withDetail(DATABASE_CONNECTION, "invalid").build();
                }
            } catch (SQLException e) {
                return org.springframework.boot.actuate.health.Health.down(e).build();
            }
        };
    }

    public HealthIndicator minioHealthIndicator() {
        return () -> {
            try {
                minioClient.listBuckets();
                return org.springframework.boot.actuate.health.Health.up().withDetail(MINIO, "reachable").build();
            } catch (MinioException e) {
                return org.springframework.boot.actuate.health.Health.down(e).build();
            } catch (Exception e) {
                return org.springframework.boot.actuate.health.Health.down(e).build();
            }
        };
    }

    @Bean
    public HealthIndicator youHealthIndicator() {
        return () -> org.springframework.boot.actuate.health.Health.up().withDetail("ai", "I am up and running!").build();
    }
}
