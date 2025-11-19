package com.rcs.ssf.service;

import com.rcs.ssf.dto.DashboardStatsDto;
import com.rcs.ssf.dto.LoginAttemptTrendPointDto;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Date;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class DashboardService {

    private static final ZoneId DASHBOARD_ZONE = ZoneId.of("UTC");
    private static final int LOGIN_TREND_WINDOW_DAYS = 7;
    private static final String UTC_TRUNC_CREATED_AT =
        "TRUNC(CAST((FROM_TZ(CAST(created_at AS TIMESTAMP), SESSIONTIMEZONE) AT TIME ZONE 'UTC') AS DATE))";
    private static final String UTC_CURRENT_DAY = "TRUNC(CAST((SYSTIMESTAMP AT TIME ZONE 'UTC') AS DATE))";
    private static final String UTC_CREATED_AT_TIMESTAMP =
        "(FROM_TZ(CAST(created_at AS TIMESTAMP), SESSIONTIMEZONE) AT TIME ZONE 'UTC')";
    private static final String UTC_CURRENT_TIMESTAMP = "(SYSTIMESTAMP AT TIME ZONE 'UTC')";
    private static final String LOGIN_TREND_SQL = Objects.requireNonNull(String.format(
        Locale.ROOT,
        """
            SELECT %1$s AS attempt_day,
                   SUM(CASE WHEN success = 1 THEN 1 ELSE 0 END) AS success_count,
                   SUM(CASE WHEN success = 0 THEN 1 ELSE 0 END) AS failed_count
            FROM audit_login_attempts
            WHERE %1$s >= %2$s - %3$d
            GROUP BY %1$s
            ORDER BY attempt_day
        """,
        UTC_TRUNC_CREATED_AT,
        UTC_CURRENT_DAY,
        LOGIN_TREND_WINDOW_DAYS - 1
    ));

    private static final RowMapper<LoginAttemptTrendPointDto> LOGIN_TREND_ROW_MAPPER = (rs, rowNum) -> {
        Date attemptDay = rs.getDate("attempt_day");
        LocalDate date = attemptDay != null ? attemptDay.toLocalDate() : null;
        return new LoginAttemptTrendPointDto(
            date,
            rs.getLong("success_count"),
            rs.getLong("failed_count")
        );
    };

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
        String activeSessionsSql = Objects.requireNonNull(String.format(
            Locale.ROOT,
            "SELECT COUNT(*) FROM audit_sessions WHERE %s > %s - INTERVAL '24' HOUR",
            UTC_CREATED_AT_TIMESTAMP,
            UTC_CURRENT_TIMESTAMP
        ));
        Long activeSessions = jt.queryForObject(activeSessionsSql, Long.class);
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
        String loginAttemptsTodaySql = Objects.requireNonNull(String.format(
            Locale.ROOT,
            "SELECT COUNT(*) FROM audit_login_attempts WHERE %s = %s",
            UTC_TRUNC_CREATED_AT,
            UTC_CURRENT_DAY
        ));
        Long loginAttemptsToday = jt.queryForObject(loginAttemptsTodaySql, Long.class);
        stats.setLoginAttemptsToday(loginAttemptsToday != null ? loginAttemptsToday : 0);
        stats.setTotalLoginAttempts(stats.getLoginAttemptsToday());

        // Failed login attempts
        String failedAttemptsSql = Objects.requireNonNull(String.format(
            Locale.ROOT,
            "SELECT COUNT(*) FROM audit_login_attempts WHERE success = 0 AND %s = %s",
            UTC_TRUNC_CREATED_AT,
            UTC_CURRENT_DAY
        ));
        Long failedAttempts = jt.queryForObject(failedAttemptsSql, Long.class);
        stats.setFailedLoginAttempts(failedAttempts != null ? failedAttempts : 0);

        stats.setLoginAttemptTrends(buildLoginAttemptTrends(jt));

        return stats;
    }

    private List<LoginAttemptTrendPointDto> buildLoginAttemptTrends(JdbcTemplate jt) {
        @SuppressWarnings("null")
        List<LoginAttemptTrendPointDto> aggregates = jt.query(LOGIN_TREND_SQL, LOGIN_TREND_ROW_MAPPER);
        List<LoginAttemptTrendPointDto> sanitizedAggregates = aggregates.stream()
            .filter(dto -> dto.getDate() != null)
            .collect(Collectors.toList());

        Map<LocalDate, LoginAttemptTrendPointDto> aggregatesByDate = sanitizedAggregates.stream()
            .collect(Collectors.toMap(
                LoginAttemptTrendPointDto::getDate,
                Function.identity(),
                (left, right) -> left,
                LinkedHashMap::new
            ));

        LocalDate today = LocalDate.now(DASHBOARD_ZONE);
        LocalDate start = today.minusDays(LOGIN_TREND_WINDOW_DAYS - 1L);

        List<LoginAttemptTrendPointDto> window = new ArrayList<>();
        for (int i = 0; i < LOGIN_TREND_WINDOW_DAYS; i++) {
            LocalDate day = start.plusDays(i);
            LoginAttemptTrendPointDto point = aggregatesByDate.get(day);
            if (point == null) {
                point = new LoginAttemptTrendPointDto(day, 0, 0);
            }
            window.add(point);
        }

        return window;
    }
}