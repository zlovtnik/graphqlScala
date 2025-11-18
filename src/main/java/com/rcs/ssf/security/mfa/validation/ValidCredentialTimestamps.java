package com.rcs.ssf.security.mfa.validation;

import com.rcs.ssf.security.mfa.credential.WebAuthnCredential;
import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import java.lang.annotation.*;

/**
 * Validates that WebAuthn credential timestamps are temporally consistent.
 *
 * Enforcement rules:
 * <ul>
 *   <li>createdAt must not be in the future (allowing 5s clock skew)</li>
 *   <li>lastUsedAt must not be in the future, unless it is 0 (sentinel for "never used")</li>
 *   <li>createdAt ≤ lastUsedAt when lastUsedAt is set (non-zero)</li>
 * </ul>
 *
 * Example usage:
 * <pre>
 * {@code
 *   @ValidCredentialTimestamps
 *   public class WebAuthnCredential {
 *       private long createdAt;
 *       private long lastUsedAt;
 *   }
 * }
 * </pre>
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ValidCredentialTimestamps.Validator.class)
public @interface ValidCredentialTimestamps {
    String message() default "Credential timestamps are invalid: createdAt must not exceed lastUsedAt, and neither should be in the future";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<ValidCredentialTimestamps, WebAuthnCredential> {
        private static final long MAX_FUTURE_DRIFT_MS = 5000; // Allow 5s clock skew

        @Override
        public boolean isValid(WebAuthnCredential cred, ConstraintValidatorContext context) {
            if (cred == null) return true;

            long now = System.currentTimeMillis();

            // Validate createdAt is not in the future
            if (cred.getCreatedAt() > now + MAX_FUTURE_DRIFT_MS) {
                return false;
            }

            // Validate lastUsedAt is not in the future (unless 0, indicating never used)
            if (cred.getLastUsedAt() != 0 && cred.getLastUsedAt() > now + MAX_FUTURE_DRIFT_MS) {
                return false;
            }

            // Validate createdAt ≤ lastUsedAt (when lastUsedAt is set)
            if (cred.getLastUsedAt() != 0 && cred.getLastUsedAt() < cred.getCreatedAt()) {
                return false;
            }

            return true;
        }
    }
}
