package com.rcs.ssf.security.mfa;

import java.util.List;

/**
 * Backup codes service for MFA recovery.
 *
 * Provides single-use codes that users can store securely to regain access
 * if they lose their primary MFA device.
 *
 * Features:
 * - Generate 10 backup codes per enrollment
 * - One-time use (consumed on verification)
 * - Audit log every consumption
 * - Admin override capability
 */
public interface BackupCodeService {

    /**
     * Generate backup codes for a user.
     *
     * Implementations must regenerate a fresh set of 10 codes (format: XXXX-XXXX-XXXX)
     * every time this method is invoked, atomically replacing any existing codes. The
     * new set must be persisted before returning and prior codes become immediately
     * invalid, ensuring callers cannot end up with multiple active sets. Repeated
     * invocations therefore act as a rotate-and-replace operation, which should be
     * serialized per user to remain thread-safe. Callers receive the plain-text codes
     * once so they can display/download them, while hashed/opaque versions are stored
    * server-side with the standard expiry semantics documented for the MFA module.
    * Implementations should throw an {@link IllegalStateException} (or domain-specific
    * exception) if persistence fails so callers know the previous codes remain active.
     *
     * @param userId user identifier
     * @return list of 10 backup codes
     */
    List<String> generateBackupCodes(String userId);

    /**
     * Verify a backup code and consume it.
     *
     * After successful verification, the code is marked as used.
     *
     * @param userId user identifier
     * @param code backup code
     * @return true if code is valid and unused, false otherwise
     */
    boolean verifyBackupCode(String userId, String code);

    /**
     * Get count of remaining backup codes for a user.
     *
     * @param userId user identifier
     * @return number of unused backup codes (0-10)
     */
    int getRemainingBackupCodeCount(String userId);

    /**
     * Regenerate backup codes (invalidates old codes).
     *
     * @param userId user identifier
     * @return list of 10 new backup codes
     */
    List<String> regenerateBackupCodes(String userId);

    /**
     * Admin override: consume a backup code on behalf of user.
     *
     * Used for account recovery by administrators.
     * Logged as "ADMIN_BACKUP_CODE_OVERRIDE" in audit trail.
     *
     * @param userId user identifier
     * @param adminId administrator performing override
     * @return true if code was consumed, false if none available
     */
    boolean adminConsumeBackupCode(String userId, String adminId);
}
