package com.rcs.ssf.dto;

import com.rcs.ssf.graphql.type.HealthStatus;
import java.util.ArrayList;
import java.util.List;

import lombok.Data;

@Data
public class DashboardStatsDto {
    private long totalUsers;
    private long activeSessions;
    private long totalAuditLogs;
    private HealthStatus systemHealth;
    private long loginAttemptsToday;
    private long failedLoginAttempts;
    private long totalLoginAttempts;
    private List<LoginAttemptTrendPointDto> loginAttemptTrends = new ArrayList<>();
}