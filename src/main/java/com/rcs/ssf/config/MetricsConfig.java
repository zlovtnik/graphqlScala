package com.rcs.ssf.config;

import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics;
import io.micrometer.core.instrument.binder.system.ProcessorMetrics;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for JVM and system-level metrics collection using Micrometer.
 * Enables comprehensive monitoring of JVM performance including garbage collection,
 * memory usage, thread utilization, and processor metrics.
 */
@Configuration
public class MetricsConfig {

    /**
     * Configures JVM garbage collection metrics.
     * Tracks GC pause times, collection counts, and memory pool statistics.
     */
    @Bean
    public JvmGcMetrics jvmGcMetrics() {
        return new JvmGcMetrics();
    }

    /**
     * Configures JVM memory metrics.
     * Monitors heap and non-heap memory usage, buffer pools, and memory pool details.
     */
    @Bean
    public JvmMemoryMetrics jvmMemoryMetrics() {
        return new JvmMemoryMetrics();
    }

    /**
     * Configures JVM thread metrics.
     * Tracks thread counts, daemon threads, and thread states (runnable, blocked, waiting, timed-waiting).
     */
    @Bean
    public JvmThreadMetrics jvmThreadMetrics() {
        return new JvmThreadMetrics();
    }

    /**
     * Configures system processor metrics.
     * Monitors CPU usage, system load average, and processor count.
     */
    @Bean
    public ProcessorMetrics processorMetrics() {
        return new ProcessorMetrics();
    }
}