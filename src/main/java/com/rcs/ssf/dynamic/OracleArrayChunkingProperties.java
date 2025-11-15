package com.rcs.ssf.dynamic;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration backing the adaptive Oracle associative array chunking policy.
 */
@Validated
@ConfigurationProperties(prefix = "oracle.array")
public class OracleArrayChunkingProperties {

    /**
     * Minimum rows processed per chunk when the JVM is under heavy pressure.
     */
    private int minChunkSize = 500;

    /**
     * Default rows per chunk when the JVM is healthy.
     */
    private int defaultChunkSize = 2_000;

    /**
     * Upper bound rows per chunk when there is ample heap headroom.
     */
    private int maxChunkSize = 10_000;

    /**
     * Eden space utilization percentage that triggers producer throttling.
     */
    private double edenPauseThresholdPercent = 80.0;

    /**
     * Amount of time (ms) to pause intake when memory pressure exceeds the threshold.
     */
    private long pauseDurationMs = 250;

    public int getMinChunkSize() {
        return minChunkSize;
    }

    public void setMinChunkSize(int minChunkSize) {
        this.minChunkSize = minChunkSize;
    }

    public int getDefaultChunkSize() {
        return defaultChunkSize;
    }

    public void setDefaultChunkSize(int defaultChunkSize) {
        this.defaultChunkSize = defaultChunkSize;
    }

    public int getMaxChunkSize() {
        return maxChunkSize;
    }

    public void setMaxChunkSize(int maxChunkSize) {
        this.maxChunkSize = maxChunkSize;
    }

    public double getEdenPauseThresholdPercent() {
        return edenPauseThresholdPercent;
    }

    public void setEdenPauseThresholdPercent(double edenPauseThresholdPercent) {
        if (edenPauseThresholdPercent < 0d || edenPauseThresholdPercent > 100d) {
            throw new IllegalArgumentException("edenPauseThresholdPercent must be between 0 and 100 inclusive");
        }
        this.edenPauseThresholdPercent = edenPauseThresholdPercent;
    }

    public long getPauseDurationMs() {
        return pauseDurationMs;
    }

    public void setPauseDurationMs(long pauseDurationMs) {
        this.pauseDurationMs = pauseDurationMs;
    }
}
