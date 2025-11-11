package com.rcs.ssf.dto;

import lombok.Data;

@Data
public class DashboardStatsDto {
    private long totalUsers;
    private long activeSessions;
    private long totalAuditLogs;
    private String systemHealth;
    private long apiCallsToday;
    private long failedLoginAttempts;
}