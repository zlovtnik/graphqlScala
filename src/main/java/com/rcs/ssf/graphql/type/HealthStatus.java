package com.rcs.ssf.graphql.type;

/**
 * Enum for system health status in GraphQL.
 * Maps to the HealthStatus GraphQL enum type.
 */
public enum HealthStatus {
    UP("UP"),
    DOWN("DOWN"),
    DEGRADED("DEGRADED");

    private final String value;

    HealthStatus(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    /**
     * Converts a string to a HealthStatus enum value.
     *
     * @param value the string value to convert
     * @return the corresponding HealthStatus enum value
     * @throws IllegalArgumentException if the value is not recognized
     */
    public static HealthStatus fromValue(String value) {
        if (value == null) {
            return UP; // default to UP if null
        }
        for (HealthStatus status : HealthStatus.values()) {
            if (status.value.equalsIgnoreCase(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown HealthStatus value: " + value);
    }
}
