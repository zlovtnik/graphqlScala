package com.rcs.ssf.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO representing an alert about the system health or a dependency
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HealthAlertDto {
    /**
     * Alert severity: INFO, WARNING, ERROR, CRITICAL
     */
    @JsonProperty("severity")
    private AlertSeverity severity;

    /**
     * Alert message
     */
    private String message;

    /**
     * Affected component (e.g., "database", "redis", "minio")
     */
    private String component;

    /**
     * Suggested action to resolve the alert
     */
    private String suggestedAction;

    /**
     * Timestamp of when the alert was raised (ISO-8601)
     */
    private String timestamp;
}
