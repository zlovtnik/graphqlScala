package com.rcs.ssf.security.mfa;

/**
 * TOTP (Time-based One-Time Password) service for MFA support.
 *
 * Responsibilities:
 * - Generate TOTP secrets during enrollment
 * - Generate otpauth URIs for QR code enrollment
 * - Verify TOTP codes with time-window tolerance
 * - Rate-limit verification attempts
 *
 * Implementation uses Google Authenticator compatible algorithm (RFC 6238).
 */
public interface TotpService {

    /**
     * Check if TOTP MFA is currently enabled for a user.
     *
     * @param userId user identifier (Long, non-null)
     * @return true if TOTP is enrolled and active, false otherwise
     */
    boolean isTotpEnabled(Long userId);

    /**
     * Generate a new TOTP secret for a user.
     *
     * @param userId user identifier (Long, non-null)
     * @return TOTP secret (base32 encoded)
     * @throws MfaVerificationException if user not found or already enrolled
     */
    String generateTotpSecret(Long userId);

    /**
     * Generate the otpauth URI for TOTP enrollment.
     *
     * Callers are expected to render the URI into an image/QR code on the client.
     *
     * @param userId     user identifier (Long, non-null)
     * @param secret     TOTP secret (base32 encoded)
     * @param issuerName issuer name for authenticator app
     * @return otpauth:// URI string for downstream QR generation
     */
    String generateOtpauthUri(Long userId, String secret, String issuerName);

    /**
     * Verify a TOTP code provided by the user.
     *
     * Tolerates 1 time window (±30 seconds) for clock skew.
     *
     * @param userId user identifier (Long, non-null)
     * @param code   6-digit TOTP code from authenticator app
     * @return true if code is valid and verification succeeds
     * @throws MfaVerificationException if verification fails permanently (e.g.,
     *                                  user TOTP secret not found, rate-limited,
     *                                  account locked, or TOTP not enabled)
     */
    boolean verifyTotpCode(Long userId, String code);

    /**
     * Confirm TOTP enrollment after successful verification.
     *
     * @param userId user identifier (Long, non-null)
     * @param secret TOTP secret to store
     * @throws MfaVerificationException if user not found, secret format invalid, or persistence fails
     */
    void confirmTotpEnrollment(Long userId, String secret);

    /**
     * Disable TOTP for a user.
     *
     * @param userId user identifier (Long, non-null)
     * @throws MfaVerificationException if user not found or TOTP not enabled
     */
    void disableTotpMfa(Long userId);
}
