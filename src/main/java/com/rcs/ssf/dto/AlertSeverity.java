package com.rcs.ssf.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

/**
 * Enum representing alert severity levels
 */
@Getter
public enum AlertSeverity {
    @JsonProperty("INFO")
    INFO("INFO"),
    
    @JsonProperty("WARNING")
    WARNING("WARNING"),
    
    @JsonProperty("ERROR")
    ERROR("ERROR"),
    
    @JsonProperty("CRITICAL")
    CRITICAL("CRITICAL");

    private final String value;

    AlertSeverity(String value) {
        this.value = value;
    }

    /**
     * Convert string value to enum, handling case-insensitive lookup
     */
    public static AlertSeverity fromValue(String value) {
        if (value == null) {
            return INFO;
        }
        for (AlertSeverity severity : AlertSeverity.values()) {
            if (severity.value.equalsIgnoreCase(value)) {
                return severity;
            }
        }
        return INFO;
    }
}
