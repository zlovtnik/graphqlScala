package com.rcs.ssf.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Validates that all required environment variables are present at startup.
 * Ensures sensitive credentials are not using unsafe defaults.
 */
@Component
public class EnvironmentValidator {

    private static final Logger logger = LoggerFactory.getLogger(EnvironmentValidator.class);

    /**
     * Checks for the presence of required environment variables.
     * Fails fast with a clear error message if any are missing.
     *
     * @throws IllegalStateException if any required environment variable is not set
     */
    public void validateRequiredEnvironmentVariables() {
        StringBuilder missingVars = new StringBuilder();

        // Check JWT_SECRET
        if (System.getenv("JWT_SECRET") == null || System.getenv("JWT_SECRET").isEmpty()) {
            missingVars.append("  - JWT_SECRET: Required for signing JWT tokens (must be ≥32 characters)\n");
        }

        // Check MINIO_ACCESS_KEY
        if (System.getenv("MINIO_ACCESS_KEY") == null || System.getenv("MINIO_ACCESS_KEY").isEmpty()) {
            missingVars.append("  - MINIO_ACCESS_KEY: Required for MinIO object storage authentication\n");
        }

        // Check MINIO_SECRET_KEY
        if (System.getenv("MINIO_SECRET_KEY") == null || System.getenv("MINIO_SECRET_KEY").isEmpty()) {
            missingVars.append("  - MINIO_SECRET_KEY: Required for MinIO object storage authentication\n");
        }

        if (missingVars.length() > 0) {
            String errorMessage = "❌ STARTUP FAILED: Missing required environment variables:\n" + missingVars +
                    "\n📚 Required Environment Variables:\n" +
                    "  • JWT_SECRET: Set to a strong, random string (≥32 characters, ≥10 distinct chars)\n" +
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
