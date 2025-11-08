package com.example.ssf.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.assertj.ApplicationContextAssert;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MinioConfig Security Validation Tests")
public class MinioConfigSecurityTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(MinioConfig.class);

    @Test
    @DisplayName("Should accept valid production configuration")
    void testValidProductionConfiguration() {
        contextRunner
                .withPropertyValues(
                        "spring.profiles.active=production",
                        "minio.url=https://secure-minio.example.com:9000",
                        "minio.access-key=secureAccessKey",
                        "minio.secret-key=secureSecretKeyMinimum8"
                )
                .run(context -> {
                    assertNotNull(context.getBean(MinioConfig.class));
                });
    }

    @Test
    @DisplayName("Should reject HTTP URL in production")
    void testRejectHttpInProduction() {
        assertThrows(IllegalStateException.class, () -> contextRunner
                .withPropertyValues(
                        "spring.profiles.active=production",
                        "minio.url=http://insecure-minio.example.com:9000",
                        "minio.access-key=secureAccessKey",
                        "minio.secret-key=secureSecretKeyMinimum8"
                )
                .run(context -> {
                    context.getBean(MinioConfig.class);
                }));
    }

    @Test
    @DisplayName("Should reject default credentials in production")
    void testRejectDefaultCredentials() {
        assertThrows(IllegalStateException.class, () -> contextRunner
                .withPropertyValues(
                        "spring.profiles.active=production",
                        "minio.url=https://secure-minio.example.com:9000",
                        "minio.access-key=minioadmin",
                        "minio.secret-key=minioadmin"
                )
                .run(context -> {
                    context.getBean(MinioConfig.class);
                }));
    }

    @Test
    @DisplayName("Should reject weak credentials in production")
    void testRejectWeakCredentials() {
        assertThrows(IllegalStateException.class, () -> contextRunner
                .withPropertyValues(
                        "spring.profiles.active=production",
                        "minio.url=https://secure-minio.example.com:9000",
                        "minio.access-key=key",
                        "minio.secret-key=short"
                )
                .run(context -> {
                    context.getBean(MinioConfig.class);
                }));
    }

    @Test
    @DisplayName("Should apply development defaults when not in production")
    void testApplyDevelopmentDefaults() {
        contextRunner
                .withPropertyValues(
                        "spring.profiles.active=development",
                        "minio.url=",
                        "minio.access-key=",
                        "minio.secret-key="
                )
                .run(context -> {
                    assertNotNull(context.getBean(MinioConfig.class));
                });
    }
}
