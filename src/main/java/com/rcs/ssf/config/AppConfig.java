package com.rcs.ssf.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class AppConfig {

    private static final Logger logger = LoggerFactory.getLogger(AppConfig.class);

    private static final int BCRYPT_MIN = 4;
    private static final int BCRYPT_MAX = 31;

    /**
     * BCrypt strength parameter for password hashing.
     * <p>
     * Expected values: 4 to 31 (inclusive). Higher values provide stronger security
     * but increase computation time exponentially.
     * </p>
     * <p>
     * Performance impact examples (approximate timings on modern hardware):
     * <ul>
     * <li>Strength 10: ~100ms per hash</li>
     * <li>Strength 12: ~400ms per hash</li>
     * <li>Strength 14: ~1600ms per hash</li>
     * </ul>
     * Recommended use-cases:
     * <ul>
     * <li>Development/testing: 10-12</li>
     * <li>Production with moderate load: 12-14</li>
     * <li>High-security environments: 14+</li>
     * </ul>
     * </p>
     */
    @Value("${security.password.bcrypt.strength:12}")
    private int bcryptStrength;

    @PostConstruct
    public void validateBcryptStrength() {
        if (bcryptStrength < BCRYPT_MIN || bcryptStrength > BCRYPT_MAX) {
            throw new IllegalStateException(
                    "security.password.bcrypt.strength must be between " + BCRYPT_MIN + " and " + BCRYPT_MAX + ", got: " + bcryptStrength);
        }
        logger.info("BCrypt strength set to {}, higher values improve security but increase computation time.", bcryptStrength);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(bcryptStrength);
    }
}
