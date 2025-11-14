package com.rcs.ssf.dto;

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
}