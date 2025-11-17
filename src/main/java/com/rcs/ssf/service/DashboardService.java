package com.rcs.ssf.service;

import com.rcs.ssf.dto.DashboardStatsDto;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.util.Optional;

@Service
public class DashboardService {

    private final Optional<JdbcTemplate> jdbcTemplate;

    public DashboardService(Optional<DataSource> dataSource) {
        this.jdbcTemplate = dataSource.map(JdbcTemplate::new);
    }

    public DashboardStatsDto getDashboardStats() {
        DashboardStatsDto stats = new DashboardStatsDto();

        if (jdbcTemplate.isEmpty()) {
            stats.setSystemHealth("UNAVAILABLE");
            return stats;
        }

        JdbcTemplate jt = jdbcTemplate.get();

        // Total users
        Long totalUsers = jt.queryForObject(
            "SELECT COUNT(*) FROM users",
            Long.class
        );
        stats.setTotalUsers(totalUsers != null ? totalUsers : 0);

        // Active sessions (sessions from last 24 hours)
        Long activeSessions = jt.queryForObject(
            "SELECT COUNT(*) FROM audit_sessions WHERE created_at > SYSDATE - INTERVAL '24' HOUR",
            Long.class
        );
        stats.setActiveSessions(activeSessions != null ? activeSessions : 0);

        // Total audit logs
        Long totalAuditLogs = jt.queryForObject(
            "SELECT COUNT(*) FROM audit_login_attempts",
            Long.class
        );
        stats.setTotalAuditLogs(totalAuditLogs != null ? totalAuditLogs : 0);

        // System health - for now, assume healthy if we can connect to DB
        stats.setSystemHealth("HEALTHY");

        // Login attempts today
        Long loginAttemptsToday = jt.queryForObject(
            "SELECT COUNT(*) FROM audit_login_attempts WHERE TRUNC(created_at) = TRUNC(SYSDATE)",
            Long.class
        );
        stats.setLoginAttemptsToday(loginAttemptsToday != null ? loginAttemptsToday : 0);
        stats.setTotalLoginAttempts(stats.getLoginAttemptsToday());

        // Failed login attempts
        Long failedAttempts = jt.queryForObject(
            "SELECT COUNT(*) FROM audit_login_attempts WHERE success = 0 AND TRUNC(created_at) = TRUNC(SYSDATE)",
            Long.class
        );
        stats.setFailedLoginAttempts(failedAttempts != null ? failedAttempts : 0);

        return stats;
    }
}