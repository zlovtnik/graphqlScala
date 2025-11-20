package com.rcs.ssf.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

/**
 * Enum representing the health status of a dependency
 */
@Getter
public enum DependencyStatus {
    @JsonProperty("UP")
    UP("UP"),
    
    @JsonProperty("DOWN")
    DOWN("DOWN"),
    
    @JsonProperty("DEGRADED")
    DEGRADED("DEGRADED"),
    
    @JsonProperty("UNKNOWN")
    UNKNOWN("UNKNOWN");

    private final String value;

    DependencyStatus(String value) {
        this.value = value;
    }

    /**
     * Convert string value to enum, handling case-insensitive lookup
     */
    public static DependencyStatus fromValue(String value) {
        if (value == null) {
            return UNKNOWN;
        }
        for (DependencyStatus status : DependencyStatus.values()) {
            if (status.value.equalsIgnoreCase(value)) {
                return status;
            }
        }
        return UNKNOWN;
    }
}
