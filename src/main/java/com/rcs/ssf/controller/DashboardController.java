package com.rcs.ssf.controller;

import com.rcs.ssf.dto.DashboardStatsDto;
import com.rcs.ssf.dto.SystemHealthDto;
import com.rcs.ssf.service.DashboardService;
import com.rcs.ssf.service.HealthService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
@PreAuthorize("isAuthenticated()")
public class DashboardController {

    private final DashboardService dashboardService;
    private final HealthService healthService;

    public DashboardController(DashboardService dashboardService, HealthService healthService) {
        this.dashboardService = dashboardService;
        this.healthService = healthService;
    }

    @GetMapping("/stats")
    public ResponseEntity<DashboardStatsDto> getStats() {
        DashboardStatsDto stats = dashboardService.getDashboardStats();
        // Ensure stats is never null; return default if service returns null
        if (stats == null) {
            stats = new DashboardStatsDto();
        }
        return ResponseEntity.ok(stats);
    }

    /**
     * Get aggregated system health with all dependencies and alerts
     * Surfaces /actuator/health, Resilience4j circuit breaker states, and dependency alerts
     * 
     * @return SystemHealthDto with dependency statuses, circuit breaker states, and active alerts
     */
    @GetMapping("/health")
    public ResponseEntity<SystemHealthDto> getHealth() {
        SystemHealthDto health = healthService.getSystemHealth();
        return ResponseEntity.ok(health);
    }
}