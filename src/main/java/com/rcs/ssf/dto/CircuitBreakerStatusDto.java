package com.rcs.ssf.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Singular;
import java.util.List;
import java.util.Map;

/**
 * DTO representing the status of Resilience4j circuit breakers
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CircuitBreakerStatusDto {
    /**
     * Individual circuit breaker states
     * Key: breaker name (e.g., "database", "redis", "minio", "auth-service", "audit-service")
     * Value: state (CLOSED, OPEN, HALF_OPEN)
     * Defaults to empty map if not provided
     */
    @Singular
    private Map<String, String> states;

    /**
     * Failure metrics for each circuit breaker
     * Key: breaker name
     * Value: failure rate percentage
     * Defaults to empty map if not provided
     */
    @Singular
    private Map<String, Double> failureRates;

    /**
     * Slow call metrics for each circuit breaker
     * Key: breaker name
     * Value: slow call rate percentage
     * Defaults to empty map if not provided
     */
    @Singular
    private Map<String, Double> slowCallRates;

    /**
     * Alerts for circuit breakers in OPEN or HALF_OPEN state
     * Defaults to empty list if not provided
     */
    @Singular
    private List<HealthAlertDto> alerts;
}
