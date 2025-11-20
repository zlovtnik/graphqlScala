package com.rcs.ssf.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

/**
 * DTO representing the overall system health and dependency statuses
 * Surfaces /actuator/health, Resilience4jConfig circuit breaker states, and dependency alerts
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SystemHealthDto {
    /**
     * Overall system status: UP, DOWN, DEGRADED
     */
    @JsonProperty("status")
    private SystemStatus status;

    /**
     * List of dependency health statuses
     */
    private List<HealthDependencyDto> dependencies;

    /**
     * List of active alerts about the system
     */
    private List<HealthAlertDto> alerts;

    /**
     * Circuit breaker states from Resilience4j
     */
    private CircuitBreakerStatusDto circuitBreakerStatus;

    /**
     * Timestamp of the health check (ISO-8601)
     */
    private String timestamp;

    /**
     * Uptime in seconds
     */
    private Long uptimeSeconds;
}
