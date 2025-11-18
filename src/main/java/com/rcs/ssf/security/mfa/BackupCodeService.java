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
     * <p><strong>Concurrency Contract (REQUIRED):</strong> Implementations MUST provide
     * per-user concurrency guarantees when multiple threads/processes invoke this method
     * simultaneously for the same user. Implementations must choose one of two behaviors:</p>
     *
     * <ul>
     *   <li><strong>(Preferred) Idempotent Generation:</strong> If concurrent calls for
     *       the same user occur, only one fresh set of codes is generated internally.
     *       All concurrent callers receive the identical plain-text codes in their return
     *       value. This ensures consistent client behavior regardless of call timing.</li>
     *   <li><strong>(Alternative) Conflict Detection:</strong> Implementations may detect
     *       concurrent generation attempts (e.g., via optimistic locking or version checks)
     *       and throw {@link java.util.ConcurrentModificationException} for concurrent callers.
     *       Callers must handle this exception and retry or fail gracefully.</li>
     * </ul>
     *
     * <p><strong>Persistence & Atomicity (REQUIRED):</strong> The new backup code set MUST be
     * persisted to durable storage before this method returns. Prior codes become immediately
     * invalid and must be rejected by {@link #verifyBackupCode(String, String)} after
     * this method completes. This ensures callers cannot end up with multiple active sets
     * or temporary states where no codes are available.</p>
     *
     * <p><strong>Rotate-and-Replace Semantics:</strong> Every invocation regenerates a fresh
     * set of 10 codes (format: XXXX-XXXX-XXXX), atomically replacing any existing codes.
     * Callers receive the plain-text codes once for display/download, while hashed/opaque
     * versions are stored server-side with standard MFA module expiry semantics.
     * {@link #regenerateBackupCodes(String)} performs equivalent semantics.</p>
     *
     * <p><strong>Error Handling:</strong> Implementations should throw an
     * {@link IllegalStateException} (or domain-specific exception) if persistence fails,
     * ensuring callers know the previous codes remain active (failed mutation detected).</p>
     *
     * <p><strong>Caller Dependencies:</strong> Downstream code (MFA enrollment flows, backup
     * code display UIs) depends on this contract being honored. Violations may result in
     * users receiving stale/invalid codes, locked-out accounts, or inconsistent MFA state.</p>
     *
     * @param userId user identifier (Long, non-null)
     * @return list of 10 newly generated backup codes in plain-text format
     * @throws IllegalArgumentException if userId is null or empty
     * @throws IllegalStateException if persistence fails and previous codes remain active
     * @throws ConcurrentModificationException if implementation uses conflict detection and concurrent generation is detected
     */
    List<String> generateBackupCodes(Long userId);

    /**
     * Verify a backup code and consume it.
     *
     * After successful verification, the code is marked as used.
     *
     * @param userId user identifier
     * @param code backup code
     * @return true if code is valid and unused, false otherwise
     */
    boolean verifyBackupCode(Long userId, String code);

    /**
     * Get count of remaining backup codes for a user.
     *
     * @param userId user identifier
     * @return number of unused backup codes (0-10)
     */
    int getRemainingBackupCodeCount(Long userId);

    /**
     * Regenerate backup codes (invalidates old codes).
     *
     * <p><strong>Rotation Semantics:</strong> This method is functionally equivalent to
     * {@link #generateBackupCodes(String)}: it atomically replaces all existing backup codes
     * with a fresh set of 10 new codes. Both methods perform the same rotate-and-replace
     * operation. Use this method when the user initiates rotation (e.g., from settings),
     * while {@link #generateBackupCodes(String)} is typically invoked during initial MFA enrollment.
     * Implementations should ensure that both paths update the same underlying store with
     * identical semantics to avoid drift.</p>
     *
     * @param userId user identifier
     * @return list of 10 new backup codes
     * @throws IllegalArgumentException if userId is null, blank, or invalid
     * @throws IllegalStateException if persistence fails (prior codes remain active)
     * @throws ConcurrentModificationException if the user's backup codes are being regenerated concurrently
     */
    List<String> regenerateBackupCodes(Long userId);

    /**
     * Admin override: consume a backup code on behalf of user.
     *
     * <p><strong>Code Selection:</strong> When multiple unused codes exist, the implementation
     * MUST consume the oldest (earliest-generated) unused code first to ensure deterministic
     * behavior and predictable audit trails. The returned boolean indicates whether any code
     * was successfully consumed (true) or if no codes remained (false). This is logged as
     * "ADMIN_BACKUP_CODE_OVERRIDE" in the audit trail with the admin ID and timestamp.</p>
     *
     * <p><strong>Authorization (REQUIRED):</strong> Implementations MUST validate that
     * the provided {@code adminId} holds an appropriate administrative permission (for
     * example, ROLE_ADMIN or ROLE_MFA_ADMIN) before consuming a backup code. The
     * method MUST throw {@link java.lang.SecurityException} if the caller lacks the
     * required privileges. Implementations MAY also use a domain-specific
     * AuthorizationException if available in your project.</p>
     *
     * <p><strong>Auditing (REQUIRED):</strong> All attempts (both successful and
     * failed) MUST be recorded in the audit trail, including adminId, userId,
     * timestamp, and a short reason for failure (if any). This ensures compliance
     * and forensic traceability.</p>
     *
     * <p>Used for account recovery by administrators in emergency scenarios.</p>
     *
     * @param userId user identifier
     * @param adminId administrator performing override
     * @return true if a code was successfully consumed, false if none were available
     * @throws SecurityException if the caller is not authorized to perform admin overrides
     */
    boolean adminConsumeBackupCode(Long userId, String adminId);
}
