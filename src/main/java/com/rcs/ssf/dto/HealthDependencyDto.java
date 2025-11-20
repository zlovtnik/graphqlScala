package com.rcs.ssf.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO representing the health status of a dependency (database, Redis, MinIO, etc.)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HealthDependencyDto {
    /**
     * Name of the dependency (e.g., "database", "redis", "minio")
     */
    private String name;

    /**
     * Status: UP, DOWN, DEGRADED, UNKNOWN
     */
    @JsonProperty("status")
    private DependencyStatus status;

    /**
     * Detailed message about the dependency status
     */
    private String detail;

    /**
     * Last check timestamp (ISO-8601)
     */
    private String lastChecked;

    /**
     * Response time in milliseconds
     */
    private Long responseTimeMs;
}
