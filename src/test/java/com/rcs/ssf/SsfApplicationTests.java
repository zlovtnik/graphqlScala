package com.rcs.ssf;

import com.rcs.ssf.config.TestDatabaseConfig;
import io.minio.MinioClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;

import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@Import(TestDatabaseConfig.class)
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "app.minio.url=http://localhost:9000",
    "app.minio.access-key=test-access-key",
    "app.minio.secret-key=test-secret-key",
    "app.jwt.secret=test-jwt-secret-with-sufficient-entropy-for-validation-purposes-abcdef123456"
})
class SsfApplicationTests {

	@MockBean
	private MinioClient minioClient;

	@Test
	void contextLoads() {
	}

}
