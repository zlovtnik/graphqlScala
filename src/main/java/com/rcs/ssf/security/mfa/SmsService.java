package com.rcs.ssf.security.mfa;

import java.util.Optional;

/**
 * SMS (Short Message Service) based MFA service.
 *
 * Responsibilities:
 * - Send SMS OTP codes
 * - Verify OTP codes with time window
 * - Rate-limit verification attempts
 * - Manage phone number enrollment
 *
 * Implementation integrates with Resilience4j for resilience (circuit breaker, retries).
 */
public interface SmsService {

    /**
     * Enroll a phone number for SMS-based MFA.
     *
     * Sends verification code to the phone number.
     *
     * @param userId user identifier
     * @param phoneNumber phone number in E.164 format (e.g., +14155552671)
     * @throws MfaProviderException if SMS delivery fails
     */
    void enrollPhoneNumber(String userId, String phoneNumber) throws MfaProviderException;

    /**
     * Verify the enrollment code sent via SMS.
     * 
     * Validates that the provided code matches the code sent during enrollment.
     * Tracks verification attempts and enforces rate limiting.
     * 
     * @param userId user identifier
     * @param code 6-digit code from SMS
     * @throws MfaVerificationException if code is invalid, expired, or rate-limit exceeded
     *         - "INVALID_CODE": code does not match enrollment code
     *         - "EXPIRED_CODE": code has expired (valid window: 10 minutes)
     *         - "RATE_LIMITED": too many verification attempts (>5 failed attempts)
     *         - "NO_ENROLLMENT_PENDING": no enrollment in progress for user
     */
    void verifyEnrollmentCode(String userId, String code);

    /**
     * Send an SMS OTP code for authentication.
     *
     * @param userId user identifier
     * @throws MfaProviderException if SMS delivery fails
     */
    void sendOtpCode(String userId) throws MfaProviderException;

    /**
     * Verify an OTP code received via SMS.
     * 
     * Validates that the provided code matches the current OTP code.
     * Tracks verification attempts and enforces rate limiting.
     * Accepts codes within ±1 time window (60 second window) for clock skew tolerance.
     *
     * @param userId user identifier
     * @param code 6-digit OTP code from SMS
     * @throws MfaVerificationException if code is invalid, expired, or rate-limit exceeded
     *         - "INVALID_CODE": code does not match current OTP
     *         - "EXPIRED_CODE": code has expired (valid window: 5 minutes)
     *         - "RATE_LIMITED": too many verification attempts (>5 failed attempts)
     *         - "OTP_NOT_SENT": no OTP currently sent for user
     */
    void verifyOtpCode(String userId, String code);

    /**
     * Disable SMS MFA for a user.
     *
     * @param userId user identifier
     */
    void disableSmsMfa(String userId);

    /**
     * Get enrolled phone number for a user (masked).
     *
     * @param userId user identifier
     * @return optional containing masked phone number (e.g., +1-415-***-2671)
     */
    Optional<String> getEnrolledPhoneNumber(String userId);
}
