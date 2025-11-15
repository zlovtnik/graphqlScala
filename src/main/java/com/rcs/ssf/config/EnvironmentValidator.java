package com.rcs.ssf.config;

import com.rcs.ssf.JwtProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Validates that all required environment variables are present at startup.
 * Ensures sensitive credentials are not using unsafe defaults.
 * 
 * Uses centralized validation logic from JwtProperties to ensure consistency
 * with runtime JWT initialization and documented constraints.
 */
@Component
public class EnvironmentValidator {

    private static final Logger logger = LoggerFactory.getLogger(EnvironmentValidator.class);

    private final Environment environment;

    public EnvironmentValidator(Environment environment) {
        this.environment = environment;
    }

    /**
     * Checks for the presence of required environment variables.
     * Fails fast with a clear error message if any are missing.
     *
     * @throws IllegalStateException if any required environment variable is not set
     */
    public void validateRequiredEnvironmentVariables() {
        StringBuilder missingVars = new StringBuilder();

        // Check JWT_SECRET using centralized validation logic
        String jwtSecret = environment.getProperty("app.jwt.secret");
        String jwtError = JwtProperties.validateSecretEntropy(jwtSecret);
        if (jwtError != null) {
            missingVars.append("  - JWT_SECRET: ").append(jwtError).append(" (required for signing JWT tokens)\n");
        }

        // Check MINIO_ACCESS_KEY
        String minioAccessKey = environment.getProperty("app.minio.access-key");
        if (minioAccessKey == null || minioAccessKey.isBlank()) {
            missingVars.append("  - MINIO_ACCESS_KEY: Required for MinIO object storage authentication\n");
        }

        // Check MINIO_SECRET_KEY
        String minioSecretKey = environment.getProperty("app.minio.secret-key");
        if (minioSecretKey == null || minioSecretKey.isBlank()) {
            missingVars.append("  - MINIO_SECRET_KEY: Required for MinIO object storage authentication\n");
        }

        // Reject known weak MinIO defaults in non-dev profiles
        String activeProfiles = System.getProperty("spring.profiles.active");
        if (activeProfiles == null || (!activeProfiles.contains("dev") && !activeProfiles.contains("local"))) {
            Set<String> weakDefaults = Set.of("minioadmin", "minio", "admin", "root", "password", "secret", "minio-access-key", "minio-secret-key");
            if ((minioAccessKey != null && weakDefaults.contains(minioAccessKey)) ||
                (minioSecretKey != null && weakDefaults.contains(minioSecretKey))) {
                missingVars.append("  - MINIO credentials: Using weak default values, please change them for security in production profiles\n");
            }
        }

        if (missingVars.length() > 0) {
            String errorMessage = "❌ STARTUP FAILED: Missing required environment variables:\n" + missingVars +
                    "\n📚 Required Environment Variables:\n" +
                    "  • JWT_SECRET: Set to a strong, random string (≥32 characters, at least min(20, length/2) distinct chars)\n" +
                    "    For example, a 32-character secret must contain at least 16 distinct characters.\n" +
                    "  • MINIO_ACCESS_KEY: MinIO access key\n" +
                    "  • MINIO_SECRET_KEY: MinIO secret key\n" +
                    "\n🔐 Production Recommendation:\n" +
                    "  Use a secrets manager (HashiCorp Vault, AWS Secrets Manager, etc.) to inject\n" +
                    "  these values securely. Do NOT commit secrets to version control.\n" +
                    "\n📖 See README.md for more details.\n";
            logger.error(errorMessage);
            throw new IllegalStateException(errorMessage);
        }

        logger.info("✅ All required environment variables are set. Proceeding with startup...");
    }
}
