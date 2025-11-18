package com.rcs.ssf.security.mfa;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.rcs.ssf.service.AuditService;
import org.mindrot.jbcrypt.BCrypt;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.concurrent.TimeUnit;

/**
 * Default implementation of {@link BackupCodeService}.
 *
 * Provides per-user thread-safe backup code generation. The per-user
 * {@link ReentrantReadWriteLock} serializes access so concurrent callers for
 * the same user are executed sequentially (later callers wait for earlier
 * ones to complete). Each completed call generates and persists a new,
 * distinct set of backup codes. To achieve true idempotency, a separate
 * cache of previously generated codes would be required; this implementation
 * intentionally regenerates codes per invocation.
 *
 * Thread-safety is achieved via per-user {@link ReentrantReadWriteLock}s to serialize
 * generation and storage updates while allowing parallel operations for different users.
 */
@Service
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.datasource", name = "enabled", havingValue = "true")
@ConditionalOnBean(JdbcTemplate.class)
public class DefaultBackupCodeService implements BackupCodeService {

    private static final int BACKUP_CODE_COUNT = 10;
    private static final String BACKUP_CODE_FORMAT = "%04X-%04X-%04X"; // XXXX-XXXX-XXXX
    private static final SecureRandom RANDOM = new SecureRandom();

    private final JdbcTemplate jdbcTemplate;
        // Per-user locks to serialize generate/regenerate operations while allowing parallel ops for different users
        // Bounded and expiring to avoid unbounded growth/memory leak
        private final Cache<String, ReentrantReadWriteLock> userLocks = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterAccess(30, TimeUnit.MINUTES)
            .build();
    private final AuditService auditService;

    /**
     * Generate backup codes for a user.
     *
     * Delegates to {@link #generateCodesInternal(Long, String)} with action tag "GENERATE"
     * to ensure consistent logic with {@link #regenerateBackupCodes(Long)}.
     *
     * @param userId user identifier (Long, non-null)
     * @return list of 10 newly generated backup codes
     * @throws IllegalArgumentException if userId is null or non-positive
     * @throws IllegalStateException if persistence fails
     */
    @Override
    public List<String> generateBackupCodes(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId must not be null");
        }
        if (userId <= 0) {
            throw new IllegalArgumentException("userId must be positive");
        }
        return generateCodesInternal(userId, "GENERATE");
    }

    /**
     * Regenerate backup codes (invalidates old codes).
     *
     * Delegates to {@link #generateCodesInternal(Long, String)} with action tag "REGENERATE"
     * to ensure identical semantics with {@link #generateBackupCodes(Long)}.
     * Both methods use the same underlying rotate-and-replace logic.
     *
     * @param userId user identifier
     * @return list of 10 new backup codes
     * @throws IllegalArgumentException if userId is null or non-positive
     * @throws IllegalStateException if persistence fails
     */
    @Override
    public List<String> regenerateBackupCodes(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId must not be null");
        }
        if (userId <= 0) {
            throw new IllegalArgumentException("userId must be positive");
        }
        return generateCodesInternal(userId, "REGENERATE");
    }

    /**
     * Internal helper that generates and persists backup codes with per-user concurrency control.
     *
     * <p><strong>Concurrency & Idempotency:</strong> Uses per-user {@link ReentrantReadWriteLock}
     * to serialize concurrent calls for the same user. Each call generates a new set of codes;
     * the lock ensures only one thread proceeds at a time for a given userId. The method is
     * NOT idempotent: each invocation produces and persists a distinct set of new codes.
     * If multiple threads call this method simultaneously for the same userId, they will be
     * serialized by the lock; each will generate fresh codes independent of the others.
     * To achieve true idempotency, implement a separate cache or call deduplication mechanism
     * at a higher level.</p>
     *
     * <p><strong>Atomicity:</strong> Code generation and persistence are atomic:
     * prior codes are invalidated and new codes are persisted in a single transaction
     * before this method returns. Callers cannot observe intermediate states.</p>
     *
     * @param userId user identifier (Long, non-null)
     * @param actionTag audit/logging tag ("GENERATE" or "REGENERATE") to track operation type
     * @return list of 10 newly generated backup codes in plain-text format
     * @throws IllegalStateException if persistence fails (prior codes remain active)
     */
    private List<String> generateCodesInternal(Long userId, String actionTag) {
        String userIdStr = userId.toString();
        ReentrantReadWriteLock lock = userLocks.get(userIdStr, k -> new ReentrantReadWriteLock());
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
     * Persist backup codes to storage and invalidate prior codes.
     *
     * @param userId user identifier (Long)
     * @param codes plain-text codes to persist (hashed before storage)
     * @param actionTag audit tag indicating operation type
     * @throws Exception if persistence fails
     */
    @Transactional
    private void persistBackupCodes(Long userId, List<String> codes, String actionTag) throws Exception {
        // Hash each code
        List<String> hashedCodes = codes.stream()
            .map(code -> BCrypt.hashpw(code, BCrypt.gensalt()))
            .toList();

        // Atomic transaction: delete old codes and insert new ones
        jdbcTemplate.update("DELETE FROM MFA_BACKUP_CODES WHERE user_id = ?", userId);
        for (String hash : hashedCodes) {
            jdbcTemplate.update("INSERT INTO MFA_BACKUP_CODES (user_id, code_hash, created_at) VALUES (?, ?, SYSTIMESTAMP)", userId, hash);
        }

        log.info("Persisted {} backup codes for user: {}", codes.size(), userId);
    }

    @Override
    @Transactional
    public boolean verifyBackupCode(Long userId, String code) {
        if (userId == null) {
            throw new IllegalArgumentException("userId must not be null");
        }
        if (userId <= 0) {
            throw new IllegalArgumentException("userId must be positive");
        }
        if (code == null) {
            auditService.logMfaEvent(userId.toString(), null, "USE_BACKUP_CODE", "BACKUP_CODE", "FAILURE", "Null code", null, null);
            return false;
        }

        // Fetch all unused hashes for the user
        List<String> hashes = jdbcTemplate.queryForList(
            "SELECT code_hash FROM MFA_BACKUP_CODES WHERE user_id = ? AND used_at IS NULL",
            String.class, userId);

        // Check each hash
        for (String storedHash : hashes) {
            if (BCrypt.checkpw(code, storedHash)) {
                // Found a match, consume it atomically
                int rowsAffected = jdbcTemplate.update(
                    "UPDATE MFA_BACKUP_CODES SET used_at = SYSTIMESTAMP WHERE user_id = ? AND code_hash = ? AND used_at IS NULL",
                    userId, storedHash);
                boolean success = rowsAffected == 1;
                auditService.logMfaEvent(userId.toString(), null, "USE_BACKUP_CODE", "BACKUP_CODE", success ? "SUCCESS" : "FAILURE",
                    success ? "Backup code consumed" : "Invalid backup code", null, null);
                log.info("Backup code verification for user: {} - {}", userId, success ? "SUCCESS" : "FAILURE");
                return success;
            }
        }

        // No match found
        auditService.logMfaEvent(userId.toString(), null, "USE_BACKUP_CODE", "BACKUP_CODE", "FAILURE", "Invalid backup code", null, null);
        log.info("Backup code verification for user: {} - FAILURE", userId);
        return false;
    }

    @Override
    public int getRemainingBackupCodeCount(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId must not be null");
        }
        if (userId <= 0) {
            throw new IllegalArgumentException("userId must be positive");
        }
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM MFA_BACKUP_CODES WHERE user_id = ? AND used_at IS NULL",
            Integer.class, userId);
        return count != null ? count : 0;
    }

    @Override
    @Transactional
    public boolean adminConsumeBackupCode(Long userId, String adminId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId must not be null");
        }
        if (userId <= 0) {
            throw new IllegalArgumentException("userId must be positive");
        }
        // Check authorization
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_ADMIN")) &&
            !auth.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_MFA_ADMIN"))) {
            auditService.logMfaEvent(userId.toString(), adminId, "ADMIN_OVERRIDE", "BACKUP_CODE", "FAILURE", "Unauthorized admin attempt", null, null);
            log.warn("Unauthorized admin backup code consumption attempt by {} for user {}", adminId, userId);
            throw new SecurityException("Insufficient privileges for admin backup code consumption");
        }

        // Find the oldest unused code
        String hash;
        try {
            hash = jdbcTemplate.queryForObject(
                "SELECT code_hash FROM MFA_BACKUP_CODES WHERE user_id = ? AND used_at IS NULL ORDER BY created_at ASC FETCH FIRST 1 ROWS ONLY",
                String.class, userId);
        } catch (EmptyResultDataAccessException e) {
            auditService.logMfaEvent(userId.toString(), adminId, "ADMIN_OVERRIDE", "BACKUP_CODE", "FAILURE", "No unused backup codes available", null, null);
            log.warn("No unused backup codes available for admin consumption by {} for user {}", adminId, userId);
            return false;
        }

        // Consume it (two-step transactional pattern: find first, then lock and update)
        int rowsAffected = jdbcTemplate.update(
            "UPDATE MFA_BACKUP_CODES SET used_at = SYSTIMESTAMP WHERE user_id = ? AND code_hash = ? AND used_at IS NULL",
            userId, hash);
        if (rowsAffected == 1) {
            auditService.logMfaEvent(userId.toString(), adminId, "ADMIN_OVERRIDE", "BACKUP_CODE", "SUCCESS", "Backup code consumed by admin", null, null);
            log.info("Admin {} consumed backup code for user {}", adminId, userId);
            return true;
        } else {
            auditService.logMfaEvent(userId.toString(), adminId, "ADMIN_OVERRIDE", "BACKUP_CODE", "FAILURE", "Concurrent consumption detected", null, null);
            return false;
        }
    }
}
