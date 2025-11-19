package com.rcs.ssf.dto;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

@Data
public class DashboardStatsDto {
    private long totalUsers;
    private long activeSessions;
    private long totalAuditLogs;
    private String systemHealth;
    private long loginAttemptsToday;
    private long failedLoginAttempts;
    private long totalLoginAttempts;
    private List<LoginAttemptTrendPointDto> loginAttemptTrends = new ArrayList<>();
}