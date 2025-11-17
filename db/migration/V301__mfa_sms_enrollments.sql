-- Flyway Migration: Create MFA SMS table
-- Description: Stores enrolled phone numbers and SMS OTP state
-- Version: V301

CREATE TABLE MFA_SMS_ENROLLMENTS (
    id NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id NUMBER(19) NOT NULL REFERENCES USERS(id) ON DELETE CASCADE,
    phone_number RAW(256) NOT NULL,  -- AES-256-GCM encrypted E.164 phone number
    is_verified NUMBER(1) DEFAULT 0,  -- 0 = pending, 1 = confirmed
    verification_code_hash VARCHAR2(128),  -- HMAC-SHA256 hash of verification code (plaintext never stored)
    verification_code_salt RAW(16),  -- Salt for verification code hash
    verification_code_expires_at TIMESTAMP,
    otp_code_hash VARCHAR2(128),  -- HMAC-SHA256 hash of OTP code (plaintext never stored)
    otp_code_salt RAW(16),  -- Salt for OTP code hash
    otp_expires_at TIMESTAMP,
    last_otp_sent_at TIMESTAMP,
    otp_send_count NUMBER DEFAULT 0,  -- Rate limiting: prevent spam
    created_at TIMESTAMP DEFAULT SYSTIMESTAMP,
    verified_at TIMESTAMP,
    updated_at TIMESTAMP DEFAULT SYSTIMESTAMP,
    UNIQUE (user_id)  -- Only one phone number per user
);

CREATE INDEX idx_mfa_sms_user_id ON MFA_SMS_ENROLLMENTS(user_id);
CREATE INDEX idx_mfa_sms_is_verified ON MFA_SMS_ENROLLMENTS(is_verified);
CREATE INDEX idx_mfa_sms_phone_expires ON MFA_SMS_ENROLLMENTS(verification_code_expires_at);
CREATE INDEX idx_mfa_sms_otp_expires ON MFA_SMS_ENROLLMENTS(otp_expires_at);

COMMENT ON TABLE MFA_SMS_ENROLLMENTS IS 'Stores SMS-based MFA enrollments and temporary OTP codes.';
COMMENT ON COLUMN MFA_SMS_ENROLLMENTS.phone_number IS 'E.164 phone number stored as AES-256-GCM ciphertext (RAW). Application decrypts before use; plaintext never persists.';
COMMENT ON COLUMN MFA_SMS_ENROLLMENTS.verification_code_hash IS 'HMAC-SHA256 hash of temporary 6-digit code for phone verification. Plaintext code is never stored. Application hashes before persisting and hashes user input for comparison.';
COMMENT ON COLUMN MFA_SMS_ENROLLMENTS.verification_code_salt IS 'Salt for verification code hash to prevent rainbow table attacks.';
COMMENT ON COLUMN MFA_SMS_ENROLLMENTS.otp_code_hash IS 'HMAC-SHA256 hash of current 6-digit OTP code sent to phone. Plaintext code is never stored. Application hashes before persisting and hashes user input for comparison.';
COMMENT ON COLUMN MFA_SMS_ENROLLMENTS.otp_code_salt IS 'Salt for OTP code hash to prevent rainbow table attacks.';
COMMENT ON COLUMN MFA_SMS_ENROLLMENTS.otp_send_count IS 'Count of OTP sends in current window for rate limiting.';
