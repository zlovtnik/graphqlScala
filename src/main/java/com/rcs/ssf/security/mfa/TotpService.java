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
     * @return true if code is valid, false otherwise
     * @throws MfaVerificationException if verification fails (e.g., rate-limited)
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
     * @param userId user identifier
     * @return optional containing secret if enabled
     */
    Optional<String> getTotpSecret(String userId);
}
