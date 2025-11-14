package com.rcs.ssf.service;

import com.rcs.ssf.dto.DashboardStatsDto;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;

@Service
public class DashboardService {

    private final JdbcTemplate jdbcTemplate;

    public DashboardService(@NonNull JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public DashboardStatsDto getDashboardStats() {
        DashboardStatsDto stats = new DashboardStatsDto();

        // Total users
        Long totalUsers = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM users",
            Long.class
        );
        stats.setTotalUsers(totalUsers != null ? totalUsers : 0);

        // Active sessions (sessions from last 24 hours)
        Long activeSessions = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM audit_sessions WHERE created_at > SYSDATE - INTERVAL '24' HOUR",
            Long.class
        );
        stats.setActiveSessions(activeSessions != null ? activeSessions : 0);

        // Total audit logs
        Long totalAuditLogs = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM audit_login_attempts",
            Long.class
        );
        stats.setTotalAuditLogs(totalAuditLogs != null ? totalAuditLogs : 0);

        // System health - for now, assume healthy if we can connect to DB
        stats.setSystemHealth("HEALTHY");

        // Login attempts today
        Long loginAttemptsToday = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM audit_login_attempts WHERE TRUNC(created_at) = TRUNC(SYSDATE)",
            Long.class
        );
        stats.setLoginAttemptsToday(loginAttemptsToday != null ? loginAttemptsToday : 0);
        stats.setTotalLoginAttempts(stats.getLoginAttemptsToday());

        // Failed login attempts
        Long failedAttempts = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM audit_login_attempts WHERE success = 0 AND TRUNC(created_at) = TRUNC(SYSDATE)",
            Long.class
        );
        stats.setFailedLoginAttempts(failedAttempts != null ? failedAttempts : 0);

        return stats;
    }
}