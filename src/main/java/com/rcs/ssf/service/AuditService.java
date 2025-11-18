package com.rcs.ssf.service;

import com.rcs.ssf.util.HashUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.sql.CallableStatement;
import java.sql.Connection;
import org.springframework.lang.Nullable;

@Service
@Slf4j
public class AuditService {

    private final @Nullable JdbcTemplate jdbcTemplate;

    public AuditService(@Nullable DataSource dataSource) {
        this.jdbcTemplate = (dataSource != null) ? new JdbcTemplate(dataSource) : null;
    }

    public void logLoginAttempt(String username, boolean success, String ipAddress, String userAgent,
            String failureReason) {
        if (this.jdbcTemplate == null) {
            log.warn("Skipping audit log: DataSource/JdbcTemplate not configured (log_login_attempt)");
            return;
        }
        this.jdbcTemplate.execute((Connection con) -> {
            try (CallableStatement cs = con.prepareCall("{ call user_pkg.log_login_attempt(?, ?, ?, ?, ?) }")) {
                cs.setString(1, username);
                cs.setInt(2, success ? 1 : 0);
                cs.setString(3, ipAddress);
                cs.setString(4, userAgent);
                cs.setString(5, failureReason);
                cs.execute();
            }
            return null;
        });
    }

    public void logSessionStart(String userId, String token, String ipAddress, String userAgent) {
        if (this.jdbcTemplate == null) {
            log.warn("Skipping audit log: DataSource/JdbcTemplate not configured (log_session_start)");
            return;
        }
        String tokenHash = HashUtils.sha256Hex(token);
        this.jdbcTemplate.execute((Connection con) -> {
            try (CallableStatement cs = con.prepareCall("{ call user_pkg.log_session_start(?, ?, ?, ?) }")) {
                cs.setString(1, userId);
                cs.setString(2, tokenHash);
                cs.setString(3, ipAddress);
                cs.setString(4, userAgent);
                cs.execute();
            }
            return null;
        });
    }

    /**
     * Log an MFA-related event to the audit trail via stored procedure
     * user_pkg.log_mfa_event.
     *
     * @param userId    affected user ID (nullable when admin-only action without
     *                  target user)
     * @param adminId   administrator ID if action performed by admin; otherwise
     *                  null
     * @param eventType event type tag (e.g., GENERATE_BACKUP, USE_BACKUP_CODE,
     *                  ADMIN_OVERRIDE)
     * @param mfaMethod MFA method (e.g., BACKUP_CODE, SMS, TOTP, WEBAUTHN)
     * @param status    outcome status (e.g., SUCCESS, FAILURE)
     * @param details   human-readable details/context (nullable)
     * @param ipAddress source IP (nullable)
     * @param userAgent user agent string (nullable)
     * @throws IllegalArgumentException if required parameters are invalid
     */
    public void logMfaEvent(String userId, String adminId, String eventType, String mfaMethod, String status,
            String details, String ipAddress, String userAgent) {
        if (this.jdbcTemplate == null) {
            log.warn("Skipping audit log: DataSource/JdbcTemplate not configured (log_mfa_event)");
            return;
        }
        // Basic validation
        if (eventType == null || eventType.isBlank()) {
            throw new IllegalArgumentException("eventType must not be null/blank");
        }
        if (mfaMethod == null || mfaMethod.isBlank()) {
            throw new IllegalArgumentException("mfaMethod must not be null/blank");
        }
        if (status == null || status.isBlank()) {
            throw new IllegalArgumentException("status must not be null/blank");
        }

        // Ensure at least one of userId or adminId is present
        if ((userId == null || userId.isBlank()) && (adminId == null || adminId.isBlank())) {
            throw new IllegalArgumentException("Either userId or adminId must be provided");
        }

        this.jdbcTemplate.execute((Connection con) -> {
            try (CallableStatement cs = con.prepareCall("{ call user_pkg.log_mfa_event(?, ?, ?, ?, ?, ?, ?, ?) }")) {
                cs.setString(1, userId);
                cs.setString(2, adminId);
                cs.setString(3, eventType);
                cs.setString(4, mfaMethod);
                cs.setString(5, status);
                cs.setString(6, details);
                cs.setString(7, ipAddress);
                cs.setString(8, userAgent);
                cs.execute();
            }
            return null;
        });
    }
}
