package com.rcs.ssf.security.mfa;

import java.util.Optional;

/**
 * TOTP (Time-based One-Time Password) service for MFA support.
 *
 * Responsibilities:
 * - Generate TOTP secrets during enrollment
 * - Generate QR codes for user scanning
 * - Verify TOTP codes with time-window tolerance
 * - Rate-limit verification attempts
 *
 * Implementation uses Google Authenticator compatible algorithm (RFC 6238).
 */
public interface TotpService {

    /**
     * Generate a new TOTP secret for a user.
     *
     * @param userId user identifier
     * @return TOTP secret (base32 encoded)
     */
    String generateTotpSecret(String userId);

    /**
     * Generate the otpauth URI for TOTP enrollment.
     *
     * Callers are expected to render the URI into an image/QR code on the client.
     *
     * @param userId user identifier
     * @param secret TOTP secret (base32 encoded)
     * @param issuerName issuer name for authenticator app
     * @return otpauth:// URI string for downstream QR generation
     */
    String generateQrCode(String userId, String secret, String issuerName);

    /**
     * Verify a TOTP code provided by the user.
     *
     * Tolerates 1 time window (±30 seconds) for clock skew.
     *
     * @param userId user identifier
     * @param code 6-digit TOTP code from authenticator app
     * @return true if code is valid and verification succeeds
     * @throws MfaVerificationException if verification fails permanently (e.g., user TOTP secret not found, rate-limited, or account locked)
     */
    boolean verifyTotpCode(String userId, String code);

    /**
     * Confirm TOTP enrollment after successful verification.
     *
     * @param userId user identifier
     * @param secret TOTP secret to store
     */
    void confirmTotpEnrollment(String userId, String secret);

    /**
     * Disable TOTP for a user.
     *
     * @param userId user identifier
     */
    void disableTotpMfa(String userId);

    /**
     * Get TOTP secret for a user (admin only).
     *
     * <strong>SECURITY CRITICAL:</strong> This method returns the raw TOTP secret and must only be exposed
     * through tightly controlled admin-only endpoints with comprehensive audit logging. Implementations
     * must treat this secret as highly sensitive:
     *
     * <ul>
     *   <li><strong>Storage:</strong> Secrets must be encrypted at rest (AES-256-GCM or equivalent) and
     *       never stored in plaintext. Use database-level encryption (Oracle TDE) or application-level encryption.</li>
     *   <li><strong>Logging:</strong> Never log the raw secret value. Log only the user ID, timestamp, and
     *       access context (admin ID, IP, audit event). Use dedicated audit tables for compliance.</li>
     *   <li><strong>Access Control:</strong> Restrict this method to authenticated admin users with explicit
     *       "ADMIN_READ_MFA_SECRETS" permission or higher. Verify permissions before returning the secret.</li>
     *   <li><strong>Recovery-Only Use Case:</strong> Ideally, expose this capability only in constrained recovery
     *       flows (e.g., manual account recovery by authorized support staff), not in regular operations.</li>
     *   <li><strong>Alternative Approach:</strong> Consider returning only obfuscated data or a dedicated
     *       admin DTO (e.g., SecretRecoveryToken with expiration) instead of the raw secret for better security posture.</li>
     * </ul>
     *
     * @param userId user identifier
     * @return optional containing secret if enabled and caller is authorized
     * @throws MfaVerificationException or SecurityException if caller lacks admin permission
     */
    Optional<String> getTotpSecret(String userId);
}
