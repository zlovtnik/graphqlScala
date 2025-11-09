package com.example.ssf;

import com.example.ssf.config.TestDatabaseConfig;
import io.minio.MinioClient;
import io.minio.messages.Bucket;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.actuate.health.Status;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthContributor;
import org.springframework.boot.actuate.health.CompositeHealthContributor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.test.context.TestPropertySource;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@Import({TestDatabaseConfig.class, HealthConfigTest.TestConfig.class})
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "minio.url=" + HealthConfigTest.MINIO_URL,
    "minio.access-key=" + HealthConfigTest.MINIO_ACCESS_KEY,
    "minio.secret-key=" + HealthConfigTest.MINIO_SECRET_KEY
})
class HealthConfigTest {
    static final String MINIO_URL = "http://localhost:9000";
    static final String MINIO_ACCESS_KEY = "test-access-key";
    static final String MINIO_SECRET_KEY = "test-secret-key";

    @Configuration
    static class TestConfig {
        @Bean
        public MinioProperties minioProperties() {
            MinioProperties properties = new MinioProperties();
            properties.setUrl(MINIO_URL);
            properties.setAccessKey(MINIO_ACCESS_KEY);
            properties.setSecretKey(MINIO_SECRET_KEY);
            return properties;
        }
    }

    @Autowired
    private Environment environment;

    @Autowired
    private DataSource dataSource;

    private Path dbFilePath;
    private HealthConfig healthConfig;

    @BeforeEach
    void setUp() throws Exception {
        // Create and configure mock MinioClient
        MinioClient minioClient = mock(MinioClient.class);
        doReturn(new ArrayList<Bucket>()).when(minioClient).listBuckets();
        
        MinioProperties minioProperties = new MinioProperties();
        minioProperties.setUrl(MINIO_URL);
        minioProperties.setAccessKey(MINIO_ACCESS_KEY);
        minioProperties.setSecretKey(MINIO_SECRET_KEY);

        healthConfig = new HealthConfig(environment, minioProperties);
        healthConfig.setMinioClient(minioClient);
        
        // Create test database file
        Path dbDir = Path.of("./data");
        if (!Files.exists(dbDir)) {
            Files.createDirectories(dbDir);
        }
        dbFilePath = dbDir.resolve("test.db");
        if (!Files.exists(dbFilePath)) {
            Files.createFile(dbFilePath);
        }
    }

    @AfterEach
    void tearDown() throws IOException {
        // Clean up test database file
        if (dbFilePath != null && Files.exists(dbFilePath)) {
            Files.delete(dbFilePath);
        }
        Path dbDir = Path.of("./data");
        if (Files.exists(dbDir)) {
            try (Stream<Path> files = Files.list(dbDir)) {
                if (files.findAny().isEmpty()) {
                    Files.delete(dbDir);
                }
            }
        }
    }

    @Test
    void testCustomHealthContributors() {
        HealthContributor contributor = healthConfig.customHealthContributors(dataSource);
        assertTrue(contributor instanceof CompositeHealthContributor);
    }

    @Test
    void testDatabaseFileHealthIndicator() {
        Health health = healthConfig.databaseFileHealthIndicator().health();
        assertEquals(Status.UP, health.getStatus());
        assertEquals("in-memory", health.getDetails().get("databaseFile"));
    }

    @Test
    void testDatabaseConnectionHealthIndicatorWithValidConnection() throws SQLException {
        Connection connection = dataSource.getConnection();
        assertTrue(connection.isValid(1));

        Health health = healthConfig.databaseConnectionHealthIndicator(dataSource).health();
        assertEquals(Status.UP, health.getStatus());
        assertEquals("valid", health.getDetails().get("databaseConnection"));
        
        connection.close();
    }

    @Test
    void testMinioHealthIndicator() throws Exception {
        Health health = healthConfig.minioHealthIndicator().health();
        assertEquals(Status.UP, health.getStatus());
        assertEquals("reachable", health.getDetails().get("minio"));
    }

    @Test
    void testYouHealthIndicator() {
        Health health = healthConfig.youHealthIndicator().health();
        assertEquals(Status.UP, health.getStatus());
        assertEquals("I am up and running!", health.getDetails().get("ai"));
    }
}
