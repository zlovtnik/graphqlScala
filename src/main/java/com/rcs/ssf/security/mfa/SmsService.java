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
    void enrollPhoneNumber(Long userId, String phoneNumber) throws MfaProviderException;

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
    void verifyEnrollmentCode(Long userId, String code) throws MfaVerificationException;

    /**
     * Send an SMS OTP code for authentication.
     *
     * @param userId user identifier
     * @throws MfaProviderException if SMS delivery fails
     */
    void sendOtpCode(Long userId) throws MfaProviderException;

    /**
     * Verify an OTP code received via SMS.
     * 
     * Validates that the provided code matches the current OTP code.
     * Tracks verification attempts and enforces rate limiting.
     * Accepts codes based on a fixed time-step algorithm; specifically:
     * <ul>
     *   <li>Time-step duration = 5 minutes.</li>
     *   <li>Verification accepts codes in the current time-step and optionally the previous and next time-steps (i.e., current ±1 time-step) to tolerate clock skew.</li>
     *   <li>Therefore the total valid window is 15 minutes (5 minutes before the current step, the current 5 minute step, and 5 minutes after).</li>
     * </ul>
     *
     * @param userId user identifier
     * @param code 6-digit OTP code from SMS
     * @throws MfaVerificationException if code is invalid, expired, or rate-limit exceeded
     *         - "INVALID_CODE": code does not match current OTP
     *         - "EXPIRED_CODE": code has expired (valid window: 15 minutes)
     *         - "RATE_LIMITED": too many verification attempts (>5 failed attempts)
     *         - "OTP_NOT_SENT": no OTP currently sent for user
     */
    void verifyOtpCode(Long userId, String code) throws MfaVerificationException;

    /**
     * Disable SMS MFA for a user.
     *
     * @param userId user identifier
     */
    void disableSmsMfa(Long userId);

    /**
     * Get enrolled phone number for a user (masked).
     *
     * @param userId user identifier
     * @return optional containing masked phone number (e.g., +1-415-***-2671)
     */
    Optional<String> getEnrolledPhoneNumber(Long userId);
}
