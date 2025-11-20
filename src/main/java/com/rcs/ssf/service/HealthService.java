package com.rcs.ssf.service;

import com.rcs.ssf.dto.AlertSeverity;
import com.rcs.ssf.dto.CircuitBreakerStatusDto;
import com.rcs.ssf.dto.DependencyStatus;
import com.rcs.ssf.dto.HealthAlertDto;
import com.rcs.ssf.dto.HealthDependencyDto;
import com.rcs.ssf.dto.SystemHealthDto;
import com.rcs.ssf.dto.SystemStatus;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.minio.MinioClient;
import io.minio.errors.MinioException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.lang.management.ManagementFactory;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Service for aggregating system health and dependency alerts.
 * 
 * Surfaces:
 * - /actuator/health endpoint data
 * - Resilience4j circuit breaker states and metrics
 * - MinIO and Redis health contributors
 * - Active alerts for degraded dependencies
 */
@Service
@Slf4j
public class HealthService {

    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final Optional<DataSource> dataSource;
    private final Optional<RedisConnectionFactory> redisConnectionFactory;
    private final MinioClient minioClient;

    public HealthService(
            CircuitBreakerRegistry circuitBreakerRegistry,
            Optional<DataSource> dataSource,
            Optional<RedisConnectionFactory> redisConnectionFactory,
            MinioClient minioClient) {
        this.circuitBreakerRegistry = circuitBreakerRegistry;
        this.dataSource = dataSource;
        this.redisConnectionFactory = redisConnectionFactory;
        this.minioClient = minioClient;
    }

    /**
     * Get aggregated system health with all dependencies and alerts
     */
    public SystemHealthDto getSystemHealth() {
        List<HealthDependencyDto> dependencies = new ArrayList<>();
        List<HealthAlertDto> alerts = new ArrayList<>();

        // Check each dependency
        checkDatabase(dependencies, alerts);
        checkRedis(dependencies, alerts);
        checkMinIO(dependencies, alerts);

        // Get circuit breaker status
        CircuitBreakerStatusDto cbStatus = getCircuitBreakerStatus();

        // Determine overall status
        SystemStatus overallStatus = determineOverallStatus(dependencies, cbStatus);

        return SystemHealthDto.builder()
                .status(overallStatus)
                .dependencies(dependencies)
                .alerts(alerts)
                .circuitBreakerStatus(cbStatus)
                .timestamp(Instant.now().atZone(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT))
                .uptimeSeconds(ManagementFactory.getRuntimeMXBean().getUptime() / 1000)
                .build();
    }

    private void checkDatabase(List<HealthDependencyDto> dependencies, List<HealthAlertDto> alerts) {
        String currentTimestamp = Instant.now().atZone(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT);
        
        if (dataSource.isEmpty()) {
            dependencies.add(HealthDependencyDto.builder()
                    .name("database")
                    .status(DependencyStatus.UNKNOWN)
                    .detail("DataSource not configured")
                    .lastChecked(currentTimestamp)
                    .responseTimeMs(0L)
                    .build());
            return;
        }

        long startTime = System.currentTimeMillis();
        try (Connection conn = dataSource.get().getConnection()) {
            boolean valid = conn.isValid(2);
            long responseTime = System.currentTimeMillis() - startTime;

            if (valid) {
                dependencies.add(HealthDependencyDto.builder()
                        .name("database")
                        .status(DependencyStatus.UP)
                        .detail("Database connection is valid")
                        .lastChecked(currentTimestamp)
                        .responseTimeMs(responseTime)
                        .build());
            } else {
                dependencies.add(HealthDependencyDto.builder()
                        .name("database")
                        .status(DependencyStatus.DOWN)
                        .detail("Database connection is invalid")
                        .lastChecked(currentTimestamp)
                        .responseTimeMs(responseTime)
                        .build());
                alerts.add(HealthAlertDto.builder()
                        .severity(AlertSeverity.ERROR)
                        .message("Database connection is invalid")
                        .component("database")
                        .suggestedAction("Check database server status and network connectivity")
                        .timestamp(currentTimestamp)
                        .build());
            }
        } catch (SQLException e) {
            long responseTime = System.currentTimeMillis() - startTime;
            log.warn("Database health check failed", e);
            dependencies.add(HealthDependencyDto.builder()
                    .name("database")
                    .status(DependencyStatus.DOWN)
                    .detail("Connection failed: " + e.getMessage())
                    .lastChecked(currentTimestamp)
                    .responseTimeMs(responseTime)
                    .build());
            alerts.add(HealthAlertDto.builder()
                    .severity(AlertSeverity.CRITICAL)
                    .message("Database connection failed: " + e.getMessage())
                    .component("database")
                    .suggestedAction("Verify database credentials, server is running, and network is accessible")
                    .timestamp(currentTimestamp)
                    .build());
        }
    }

    private void checkRedis(List<HealthDependencyDto> dependencies, List<HealthAlertDto> alerts) {
        String currentTimestamp = Instant.now().atZone(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT);
        
        if (redisConnectionFactory.isEmpty()) {
            dependencies.add(HealthDependencyDto.builder()
                    .name("redis")
                    .status(DependencyStatus.UNKNOWN)
                    .detail("Redis connection factory not configured")
                    .lastChecked(currentTimestamp)
                    .responseTimeMs(0L)
                    .build());
            return;
        }

        long startTime = System.currentTimeMillis();
        try {
            try (var connection = redisConnectionFactory.get().getConnection()) {
                connection.ping();
            }
            long responseTime = System.currentTimeMillis() - startTime;

            dependencies.add(HealthDependencyDto.builder()
                    .name("redis")
                    .status(DependencyStatus.UP)
                    .detail("Redis connection is healthy")
                    .lastChecked(currentTimestamp)
                    .responseTimeMs(responseTime)
                    .build());
        } catch (Exception e) {
            long responseTime = System.currentTimeMillis() - startTime;
            log.warn("Redis health check failed", e);
            dependencies.add(HealthDependencyDto.builder()
                    .name("redis")
                    .status(DependencyStatus.DOWN)
                    .detail("Connection failed: " + e.getMessage())
                    .lastChecked(currentTimestamp)
                    .responseTimeMs(responseTime)
                    .build());
            alerts.add(HealthAlertDto.builder()
                    .severity(AlertSeverity.WARNING)
                    .message("Redis connection failed: " + e.getMessage())
                    .component("redis")
                    .suggestedAction("Verify Redis server is running and accessible")
                    .timestamp(currentTimestamp)
                    .build());
        }
    }

    private void checkMinIO(List<HealthDependencyDto> dependencies, List<HealthAlertDto> alerts) {
        String currentTimestamp = Instant.now().atZone(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT);
        
        long startTime = System.currentTimeMillis();
        try {
            minioClient.listBuckets();
            long responseTime = System.currentTimeMillis() - startTime;

            dependencies.add(HealthDependencyDto.builder()
                    .name("minio")
                    .status(DependencyStatus.UP)
                    .detail("MinIO is reachable and operational")
                    .lastChecked(currentTimestamp)
                    .responseTimeMs(responseTime)
                    .build());
        } catch (MinioException e) {
            long responseTime = System.currentTimeMillis() - startTime;
            log.warn("MinIO health check failed", e);
            dependencies.add(HealthDependencyDto.builder()
                    .name("minio")
                    .status(DependencyStatus.DOWN)
                    .detail("MinIO error: " + e.getMessage())
                    .lastChecked(currentTimestamp)
                    .responseTimeMs(responseTime)
                    .build());
            alerts.add(HealthAlertDto.builder()
                    .severity(AlertSeverity.WARNING)
                    .message("MinIO health check failed: " + e.getMessage())
                    .component("minio")
                    .suggestedAction("Check MinIO server status and credentials")
                    .timestamp(currentTimestamp)
                    .build());
        } catch (Exception e) {
            long responseTime = System.currentTimeMillis() - startTime;
            log.warn("MinIO health check failed with exception", e);
            dependencies.add(HealthDependencyDto.builder()
                    .name("minio")
                    .status(DependencyStatus.DOWN)
                    .detail("Error: " + e.getMessage())
                    .lastChecked(currentTimestamp)
                    .responseTimeMs(responseTime)
                    .build());
            alerts.add(HealthAlertDto.builder()
                    .severity(AlertSeverity.WARNING)
                    .message("MinIO unavailable: " + e.getMessage())
                    .component("minio")
                    .suggestedAction("Verify MinIO endpoint, access key, and secret key are correctly configured")
                    .timestamp(currentTimestamp)
                    .build());
        }
    }

    /**
     * Get circuit breaker status from Resilience4j
     */
    private CircuitBreakerStatusDto getCircuitBreakerStatus() {
        String currentTimestamp = Instant.now().atZone(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT);
        
        Map<String, String> states = new HashMap<>();
        Map<String, Double> failureRates = new HashMap<>();
        Map<String, Double> slowCallRates = new HashMap<>();
        List<HealthAlertDto> alerts = new ArrayList<>();

        for (CircuitBreaker cb : circuitBreakerRegistry.getAllCircuitBreakers()) {
            String name = cb.getName();
            states.put(name, cb.getState().toString());
            failureRates.put(name, (double) cb.getMetrics().getFailureRate());
            slowCallRates.put(name, (double) cb.getMetrics().getSlowCallRate());

            // Add alert if circuit breaker is OPEN or HALF_OPEN
            if ("OPEN".equals(cb.getState().toString())) {
                log.warn("Circuit breaker '{}' is OPEN", name);
                alerts.add(HealthAlertDto.builder()
                        .severity(AlertSeverity.CRITICAL)
                        .message("Circuit breaker '" + name + "' is OPEN - requests are being blocked")
                        .component("circuit-breaker:" + name)
                        .suggestedAction("Check the health of the " + name + " service and resolve underlying issues")
                        .timestamp(currentTimestamp)
                        .build());
            } else if ("HALF_OPEN".equals(cb.getState().toString())) {
                log.info("Circuit breaker '{}' is HALF_OPEN", name);
                alerts.add(HealthAlertDto.builder()
                        .severity(AlertSeverity.WARNING)
                        .message("Circuit breaker '" + name + "' is HALF_OPEN - testing recovery")
                        .component("circuit-breaker:" + name)
                        .suggestedAction("Monitor recovery of the " + name + " service")
                        .timestamp(currentTimestamp)
                        .build());
            }
        }

        return CircuitBreakerStatusDto.builder()
                .states(states)
                .failureRates(failureRates)
                .slowCallRates(slowCallRates)
                .alerts(alerts)
                .build();
    }

    /**
     * Determine overall system status based on dependencies and circuit breakers
     */
    private SystemStatus determineOverallStatus(List<HealthDependencyDto> dependencies, CircuitBreakerStatusDto cbStatus) {
        // Check if any critical dependency is down
        boolean hasCriticalDown = dependencies.stream()
                .anyMatch(dep -> "database".equals(dep.getName()) && DependencyStatus.DOWN == dep.getStatus());

        if (hasCriticalDown) {
            return SystemStatus.DOWN;
        }

        // Check if any critical circuit breaker is open
        boolean hasOpenCritical = cbStatus.getStates().entrySet().stream()
                .anyMatch(entry -> "auth-service".equals(entry.getKey()) && "OPEN".equals(entry.getValue()));

        if (hasOpenCritical) {
            return SystemStatus.DOWN;
        }

        // Check if any non-critical dependency is down
        boolean hasNonCriticalDown = dependencies.stream()
                .anyMatch(dep -> !("database".equals(dep.getName())) && DependencyStatus.DOWN == dep.getStatus());

        if (hasNonCriticalDown) {
            return SystemStatus.DEGRADED;
        }

        // Check if any circuit breaker is open
        if (cbStatus.getStates().values().stream().anyMatch(state -> "OPEN".equals(state))) {
            return SystemStatus.DEGRADED;
        }

        return SystemStatus.UP;
    }
}
