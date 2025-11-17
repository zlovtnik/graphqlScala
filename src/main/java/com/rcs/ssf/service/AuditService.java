package com.rcs.ssf.service;

import com.rcs.ssf.util.HashUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.util.Optional;

@Service
@Slf4j
public class AuditService {

    private final Optional<JdbcTemplate> jdbcTemplate;

    public AuditService(Optional<DataSource> dataSource) {
        this.jdbcTemplate = dataSource.map(JdbcTemplate::new);
    }

    public void logLoginAttempt(String username, boolean success, String ipAddress, String userAgent, String failureReason) {
        jdbcTemplate.ifPresent(jt -> jt.execute((Connection con) -> {
            try (CallableStatement cs = con.prepareCall("{ call user_pkg.log_login_attempt(?, ?, ?, ?, ?) }")) {
                cs.setString(1, username);
                cs.setInt(2, success ? 1 : 0);
                cs.setString(3, ipAddress);
                cs.setString(4, userAgent);
                cs.setString(5, failureReason);
                cs.execute();
            }
            return null;
        }));
    }

    public void logSessionStart(String userId, String token, String ipAddress, String userAgent) {
        String tokenHash = HashUtils.sha256Hex(token);
        jdbcTemplate.ifPresent(jt -> jt.execute((Connection con) -> {
            try (CallableStatement cs = con.prepareCall("{ call user_pkg.log_session_start(?, ?, ?, ?) }")) {
                cs.setString(1, userId);
                cs.setString(2, tokenHash);
                cs.setString(3, ipAddress);
                cs.setString(4, userAgent);
                cs.execute();
            }
            return null;
        }));
    }

    public void logMfaEvent(String userId, String adminId, String eventType, String mfaMethod, String status, String details, String ipAddress, String userAgent) {
        jdbcTemplate.ifPresent(jt -> jt.execute((Connection con) -> {
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
        }));
    }
}
