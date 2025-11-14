package com.rcs.ssf.service;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for cache service.
 * Properties are read from application.yml under the "cache.config" section.
 * 
 * Example configuration:
 * cache:
 *   config:
 *     query-result-cache:
 *       max-size: 1000
 *       ttl-minutes: 15
 *     session-cache:
 *       max-size: 5000
 *       ttl-minutes: 60
 */
@Component
@ConfigurationProperties(prefix = "cache.config")
@Data
@NoArgsConstructor
public class CacheConfiguration {

    /**
     * Configuration for query result cache.
     */
    private CacheProperties queryResultCache = new CacheProperties(1000, 15);

    /**
     * Configuration for session cache.
     */
    private CacheProperties sessionCache = new CacheProperties(5000, 60);

    /**
     * Threshold used to determine cache memory pressure as a fraction of max size (0.0 - 1.0).
     */
    private double memoryPressureThreshold = 0.8;

    /**
     * Properties for individual cache configuration.
     */
    @Data
    @NoArgsConstructor
    public static class CacheProperties {
        /**
         * Maximum number of entries in the cache.
         */
        private long maxSize = 1000;

        /**
         * Time-to-live in minutes.
         */
        private long ttlMinutes = 15;

        public CacheProperties(long maxSize, long ttlMinutes) {
            this.maxSize = maxSize;
            this.ttlMinutes = ttlMinutes;
        }
    }

    public double getMemoryPressureThreshold() {
        return memoryPressureThreshold;
    }

    public void setMemoryPressureThreshold(double memoryPressureThreshold) {
        if (memoryPressureThreshold < 0.0 || memoryPressureThreshold > 1.0) {
            throw new IllegalArgumentException("memoryPressureThreshold must be between 0.0 and 1.0");
        }
        this.memoryPressureThreshold = memoryPressureThreshold;
    }
}
