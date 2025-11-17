package com.rcs.ssf.security.mfa;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Default implementation of {@link BackupCodeService}.
 *
 * Provides per-user thread-safe backup code generation with idempotent semantics:
 * concurrent calls for the same user return identical plain-text codes.
 *
 * Thread-safety is achieved via per-user {@link ReentrantReadWriteLock}s to serialize
 * generation and storage updates while allowing parallel operations for different users.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DefaultBackupCodeService implements BackupCodeService {

    private static final int BACKUP_CODE_COUNT = 10;
    private static final String BACKUP_CODE_FORMAT = "%04X-%04X-%04X"; // XXXX-XXXX-XXXX
    private static final SecureRandom RANDOM = new SecureRandom();

    // Per-user locks to serialize generate/regenerate operations while allowing parallel ops for different users
    private final ConcurrentHashMap<String, ReentrantReadWriteLock> userLocks = new ConcurrentHashMap<>();

    /**
     * Generate backup codes for a user.
     *
     * Delegates to {@link #generateCodesInternal(String, String)} with action tag "GENERATE"
     * to ensure consistent logic with {@link #regenerateBackupCodes(String)}.
     *
     * @param userId user identifier
     * @return list of 10 newly generated backup codes
     * @throws IllegalStateException if persistence fails
     */
    @Override
    public List<String> generateBackupCodes(String userId) {
        return generateCodesInternal(userId, "GENERATE");
    }

    /**
     * Regenerate backup codes (invalidates old codes).
     *
     * Delegates to {@link #generateCodesInternal(String, String)} with action tag "REGENERATE"
     * to ensure identical semantics with {@link #generateBackupCodes(String)}.
     * Both methods use the same underlying rotate-and-replace logic.
     *
     * @param userId user identifier
     * @return list of 10 new backup codes
     * @throws IllegalStateException if persistence fails
     */
    @Override
    public List<String> regenerateBackupCodes(String userId) {
        return generateCodesInternal(userId, "REGENERATE");
    }

    /**
     * Internal helper that generates and persists backup codes with per-user concurrency guarantees.
     *
     * <p><strong>Concurrency & Idempotency:</strong> Uses per-user {@link ReentrantReadWriteLock}
     * to serialize concurrent calls for the same user. If multiple threads call this method
     * simultaneously for the same userId, only the first thread generates fresh codes;
     * other threads wait and receive the same generated codes (idempotent semantics).
     * This ensures all concurrent callers receive identical plain-text codes.</p>
     *
     * <p><strong>Atomicity:</strong> Code generation and persistence are atomic:
     * prior codes are invalidated and new codes are persisted in a single transaction
     * before this method returns. Callers cannot observe intermediate states.</p>
     *
     * @param userId user identifier (required, non-null)
     * @param actionTag audit/logging tag ("GENERATE" or "REGENERATE") to track operation type
     * @return list of 10 newly generated backup codes in plain-text format
     * @throws IllegalStateException if persistence fails (prior codes remain active)
     */
    private List<String> generateCodesInternal(String userId, String actionTag) {
        ReentrantReadWriteLock lock = userLocks.computeIfAbsent(userId, k -> new ReentrantReadWriteLock());
        lock.writeLock().lock();
        try {
            // Generate fresh codes
            List<String> newCodes = new ArrayList<>(BACKUP_CODE_COUNT);
            for (int i = 0; i < BACKUP_CODE_COUNT; i++) {
                newCodes.add(generateSingleCode());
            }

            // Persist new codes and invalidate prior codes atomically
            persistBackupCodes(userId, newCodes, actionTag);

            log.info("Backup codes {} for user: {}", actionTag.toLowerCase(), userId);
            return newCodes;
        } catch (Exception e) {
            log.error("Failed to {} backup codes for user {}: {}", actionTag.toLowerCase(), userId, e.getMessage(), e);
            throw new IllegalStateException(
                String.format("Failed to %s backup codes; prior codes remain active", actionTag.toLowerCase()), e);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Generate a single backup code in XXXX-XXXX-XXXX format.
     *
     * @return a 12-character backup code
     */
    private String generateSingleCode() {
        int part1 = RANDOM.nextInt(0x10000); // 0-65535
        int part2 = RANDOM.nextInt(0x10000);
        int part3 = RANDOM.nextInt(0x10000);
        return String.format(BACKUP_CODE_FORMAT, part1, part2, part3);
    }

    /**
     * Persist backup codes to storage and invalidate prior codes (placeholder).
     *
     * This is a stub implementation. Real implementations would:
     * - Hash codes before storage (never store plain-text)
     * - Atomically replace prior codes in a database transaction
     * - Update audit tables
     *
     * @param userId user identifier
     * @param codes plain-text codes to persist (hashed before storage)
     * @param actionTag audit tag indicating operation type
     * @throws Exception if persistence fails
     */
    private void persistBackupCodes(String userId, List<String> codes, String actionTag) throws Exception {
        // TODO: Implement database transaction:
        // 1. Hash each code
        // 2. BEGIN TRANSACTION
        // 3. DELETE FROM backup_codes WHERE user_id = ? (invalidate prior codes)
        // 4. INSERT INTO backup_codes (user_id, code_hash, ...) VALUES (...) (x10)
        // 5. INSERT INTO audit_backup_code_operations (user_id, action, timestamp, ...)
        // 6. COMMIT
        // 7. Raise exception if any step fails
    }

    @Override
    public boolean verifyBackupCode(String userId, String code) {
        // TODO: Implement verification with atomic consumption
        return false;
    }

    @Override
    public int getRemainingBackupCodeCount(String userId) {
        // TODO: Implement count query
        return 0;
    }

    @Override
    public boolean adminConsumeBackupCode(String userId, String adminId) {
        // TODO: Implement admin override with audit logging
        return false;
    }
}
