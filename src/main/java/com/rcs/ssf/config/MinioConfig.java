package com.rcs.ssf.config;

import io.minio.MinioClient;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Configuration
public class MinioConfig {

    private static final Logger logger = LoggerFactory.getLogger(MinioConfig.class);

    @Value("${app.minio.url:}")
    private String minioUrl;

    @Value("${app.minio.access-key:}")
    private String minioAccessKey;

    @Value("${app.minio.secret-key:}")
    private String minioSecretKey;

    private final Environment environment;

    public MinioConfig(Environment environment) {
        this.environment = environment;
    }

    @PostConstruct
    public void validateMinioConfiguration() {
        validateConfiguration();
    }

    private void validateProductionConfiguration() {
        // Check if values are provided
        if (minioUrl == null || minioUrl.trim().isEmpty()) {
            throw new IllegalStateException(
                    "Production profile detected: minio.url must be explicitly configured. " +
                    "Provide via application-prod.yml, application-production.yml, or MINIO_URL environment variable.");
        }

        if (minioAccessKey == null || minioAccessKey.trim().isEmpty()) {
            throw new IllegalStateException(
                    "Production profile detected: minio.access-key must be explicitly configured. " +
                    "Provide via application-prod.yml, application-production.yml, or MINIO_ACCESS_KEY environment variable.");
        }

        if (minioSecretKey == null || minioSecretKey.trim().isEmpty()) {
            throw new IllegalStateException(
                    "Production profile detected: minio.secret-key must be explicitly configured. " +
                    "Provide via application-prod.yml, application-production.yml, or MINIO_SECRET_KEY environment variable.");
        }

        // Validate URL uses HTTPS
        if (!minioUrl.startsWith("https://")) {
            throw new IllegalStateException("MINIO URL must use HTTPS");
        }

        // Check for default credentials
        if ("minioadmin".equals(minioAccessKey) || "minioadmin".equals(minioSecretKey)) {
            throw new IllegalStateException("Minio credentials must not use default 'minioadmin' username or secret");
        }

        // Warn if credentials are weak (too short)
        if (minioAccessKey.length() < 3 || minioSecretKey.length() < 16) {
            throw new IllegalStateException("Minio access key must be at least 3 characters and secret key at least 16 characters");
        }
    }

    private void applyNonProductionDefaults() {
        // Apply development defaults only if not explicitly configured
        if (minioUrl == null || minioUrl.trim().isEmpty()) {
            minioUrl = "http://localhost:9000";
            logger.info("[MinIO] Using development default URL: http://localhost:9000");
        }

        if (minioAccessKey == null || minioAccessKey.trim().isEmpty()) {
            minioAccessKey = "minioadmin";
            logger.info("[MinIO] Using development default access key: minioadmin");
        }

        if (minioSecretKey == null || minioSecretKey.trim().isEmpty()) {
            minioSecretKey = "minioadmin";
            logger.info("[MinIO] Using development default secret key: minioadmin");
        }

        logger.warn("[MinIO] ⚠️  WARNING: Using development defaults for MinIO. " +
                "Do NOT use these defaults in production!");
    }

    @Bean
    public MinioClient minioClient() {
        return MinioClient.builder()
                .endpoint(minioUrl)
                .credentials(minioAccessKey, minioSecretKey)
                .build();
    }

    private void validateConfiguration() {
        String[] activeProfiles = environment.getActiveProfiles();
        boolean isProduction = java.util.Arrays.asList(activeProfiles).contains("prod") 
                || java.util.Arrays.asList(activeProfiles).contains("production");

        // In production, enforce secure configuration
        if (isProduction) {
            validateProductionConfiguration();
        } else {
            // In non-production, apply safe defaults and warn if using defaults
            applyNonProductionDefaults();
        }
    }
}
