# Multi-Factor Authentication (MFA) Implementation

## Overview

This document details the design and implementation plan for multi-factor authentication in Phase 1, supporting TOTP, SMS, WebAuthn, and backup codes.

## Purpose

- Design multi-factor authentication architecture
- Specify supported authentication factors
- Define enrollment and verification flows
- Establish security requirements for each factor

## Owner

Authentication & Security team

## Supported Authentication Factors

### Phase 1 - Initial Release
- **TOTP** (Time-based One-Time Password): RFC 6238 implementation with 30-second time windows
- **SMS**: SMS-based OTP delivery via third-party provider
- **Backup Codes**: Single-use recovery codes for account recovery
- **WebAuthn** (optional): FIDO2 hardware key support (lower priority)

### Phase 1+ - Future Enhancements
- **Email OTP**: Email-based one-time passwords
- **Push Notifications**: Mobile app push-based approval
- **Risk-Based Authentication**: Conditional MFA based on login risk factors

## MFA Architecture

### Database Schema
- `MFA_TOTP_SECRETS`: TOTP secret storage (encrypted)
- `MFA_SMS_ENROLLMENTS`: SMS phone number storage and verification status
- `MFA_BACKUP_CODES`: Backup code storage (hashed, single-use)

**Migration**: See db/migration/V300__mfa_totp_secrets.sql, V301__mfa_sms_enrollments.sql

### GraphQL API
- `Query.mfaStatus`: Current MFA enrollment status
- `Mutation.enrollMfa`: Begin MFA enrollment flow
- `Mutation.verifyMfa`: Verify MFA factor during enrollment
- `Mutation.disableMfa`: Disable MFA factor
- `Mutation.generateBackupCodes`: Generate new backup codes

## Enrollment & Verification Flows

### TOTP Enrollment
1. User requests TOTP enrollment
2. Server generates secret and returns QR code
3. User scans QR code in authenticator app
4. User verifies 6-digit code from app
5. Secret stored encrypted in database

### SMS Enrollment
1. User provides phone number
2. Server sends SMS OTP to phone
3. User verifies OTP
4. Phone number marked as verified

### Backup Code Generation
1. Server generates 10 single-use recovery codes
2. Codes presented to user one-time (hashed in database)
3. User stores codes in secure location

## Security Considerations

- [ ] TOTP secrets encrypted using TDE (Phase 3)
- [ ] Rate limiting on MFA verification attempts
- [ ] Audit logging for MFA enrollment/disablement
- [ ] Account recovery procedures for lost MFA devices

## Sections to Be Filled In Later

- [ ] WebAuthn integration specification
- [ ] SMS provider selection and integration details
- [ ] MFA bypass procedures for support team
- [ ] Mobile app integration guide
- [ ] API specification and rate limits
- [ ] Testing strategy and test cases

## References

- RFC 6238: TOTP specification
- See SECURITY_ARCHITECTURE.md for authentication context
- See README.md for Phase roadmap
