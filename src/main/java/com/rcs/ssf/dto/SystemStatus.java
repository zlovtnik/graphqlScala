package com.rcs.ssf.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

/**
 * Enum representing the overall system health status
 */
@Getter
public enum SystemStatus {
    @JsonProperty("UP")
    UP("UP"),
    
    @JsonProperty("DOWN")
    DOWN("DOWN"),
    
    @JsonProperty("DEGRADED")
    DEGRADED("DEGRADED");

    private final String value;

    SystemStatus(String value) {
        this.value = value;
    }

    /**
     * Convert string value to enum, handling case-insensitive lookup
     */
    public static SystemStatus fromValue(String value) {
        if (value == null) {
            return UP;
        }
        for (SystemStatus status : SystemStatus.values()) {
            if (status.value.equalsIgnoreCase(value)) {
                return status;
            }
        }
        return UP;
    }
}
