package com.rcs.ssf.security.mfa.validation;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import java.lang.annotation.*;
import java.util.Base64;

/**
 * Validates that a string is valid Base64 encoding (standard or URL-safe).
 * Accepts strings with proper padding or without padding.
 * 
 * Enforces a maximum Base64 string length to prevent decoding extremely large
 * inputs
 * that could cause performance issues or memory exhaustion.
 *
 * Example usage:
 * 
 * <pre>
 * {@code
 * @ValidBase64(message = "Challenge must be valid Base64 encoding")
 * private String challenge;
 * }
 * </pre>
 *
 * This validator allows both standard and URL-safe Base64 decoders,
 * and gracefully handles null or blank values (delegating to @NotBlank).
 */
@Target({ ElementType.FIELD, ElementType.PARAMETER })
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ValidBase64.Validator.class)
public @interface ValidBase64 {
    String message() default "Invalid Base64 encoding";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<ValidBase64, String> {
        // Maximum allowed Base64 string length (4096 characters = ~3KB decoded)
        private static final int MAX_BASE64_LENGTH = 4096;

        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            if (value == null || value.isBlank()) {
                // Null/blank is handled by @NotBlank; this validator only checks format
                return true;
            }

            // Reject extremely large Base64 strings before attempting decode
            if (value.length() > MAX_BASE64_LENGTH) {
                return false;
            }

            try {
                // Try standard Base64 decoder first
                Base64.getDecoder().decode(value);
                return true;
            } catch (IllegalArgumentException e) {
                // Try URL-safe decoder as fallback
                try {
                    Base64.getUrlDecoder().decode(value);
                    return true;
                } catch (IllegalArgumentException ex) {
                    // Neither decoder succeeded; invalid Base64
                    return false;
                }
            }
        }
    }
}
