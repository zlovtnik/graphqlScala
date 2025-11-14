package com.rcs.ssf;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * JWT configuration properties with centralized validation logic.
 * 
 * Constraint: JWT_SECRET must be ≥32 characters with at least min(20, length/2) distinct characters.
 * For example, a 32-character secret must contain at least 16 distinct characters.
 */
@Validated
@ConfigurationProperties(prefix = "app.jwt")
@Component
public class JwtProperties {
    @NotBlank(message = "app.jwt.secret must not be blank")
    @Size(min = 32, max = 512, message = "app.jwt.secret must be between {min} and {max} characters")
    private String secret;

    @JsonIgnore
    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    /**
     * Centralized JWT secret entropy validation logic.
     * 
     * @param secret the JWT secret to validate
     * @return validation error message if invalid, or null if valid
     */
    @JsonIgnore
    public static String validateSecretEntropy(String secret) {
        if (secret == null || secret.isBlank()) {
            return "JWT_SECRET must not be blank";
        }
        
        if (secret.length() < 32) {
            return "JWT_SECRET must be at least 32 characters long (found " + secret.length() + ")"; 
        }
        
        long distinctChars = secret.chars().distinct().count();
        long requiredDistinct = Math.min(20, secret.length() / 2);
        
        if (distinctChars < requiredDistinct) {
            return "JWT_SECRET must contain at least " + requiredDistinct + " distinct characters (found " + distinctChars + ")"; 
        }
        
        return null; // Valid
    }

    @Override
    public String toString() {
        return "JwtProperties{secret='[PROTECTED]'}";
    }
}
