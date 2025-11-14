package com.rcs.ssf.dynamic;

import com.rcs.ssf.metrics.BatchMetricsRecorder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryUsage;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Computes adaptive chunk sizes for Oracle associative array operations to reduce GC spikes during
 * bulk stored procedure calls.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ArrayChunkingPolicy {

    private final OracleArrayChunkingProperties properties;
    private final BatchMetricsRecorder batchMetricsRecorder;
    private final List<MemoryPoolMXBean> edenPools = ManagementFactory.getMemoryPoolMXBeans().stream()
            .filter(pool -> pool.getName().toLowerCase(Locale.ROOT).contains("eden"))
            .toList();

    public ChunkDecision evaluate(int totalRows) {
        MemorySnapshot snapshot = captureSnapshot();
        double usagePercent = snapshot.usagePercent();
        double threshold = clampThreshold(properties.getEdenPauseThresholdPercent());
        int targetChunk = calculateChunkSize(usagePercent, threshold);
        int chunkSize = totalRows <= 0 ? 0 : Math.min(targetChunk, totalRows);

        boolean throttle = usagePercent >= threshold;
        Duration pauseDuration = throttle ? Duration.ofMillis(properties.getPauseDurationMs()) : Duration.ZERO;

        batchMetricsRecorder.recordMemoryPressure("oracle-array-chunking", snapshot.usedBytes(), snapshot.maxBytes());

        if (log.isDebugEnabled()) {
            log.debug("Oracle array chunking decision: rows={}, chunkSize={}, edenUsage={}%, throttle={}",
                    totalRows, chunkSize, String.format(Locale.ROOT, "%.1f", usagePercent), throttle);
        }

        return new ChunkDecision(chunkSize, usagePercent, throttle, pauseDuration);
    }

    private int calculateChunkSize(double usagePercent, double threshold) {
        double normalizedHeadroom = Math.max(0d, threshold - usagePercent) / Math.max(threshold, 1d);
        int candidate = (int) Math.round(properties.getDefaultChunkSize() +
                normalizedHeadroom * (properties.getMaxChunkSize() - properties.getDefaultChunkSize()));
        return Math.min(properties.getMaxChunkSize(),
                Math.max(properties.getMinChunkSize(), candidate));
    }

    private double clampThreshold(double configuredThreshold) {
        return Math.max(0d, Math.min(100d, configuredThreshold));
    }

    private MemorySnapshot captureSnapshot() {
        Optional<MemoryUsage> edenUsage = edenPools.stream()
                .map(MemoryPoolMXBean::getUsage)
                .filter(usage -> usage != null && usage.getMax() > 0)
                .findFirst();

        if (edenUsage.isPresent()) {
            MemoryUsage usage = edenUsage.get();
            return new MemorySnapshot(usage.getUsed(), usage.getMax());
        }

        MemoryUsage heapUsage = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        long used = heapUsage.getUsed();
        long max = heapUsage.getMax() > 0 ? heapUsage.getMax() : Runtime.getRuntime().maxMemory();
        return new MemorySnapshot(used, max);
    }

    public record ChunkDecision(int chunkSize, double edenUsagePercent, boolean throttleProducer, Duration pauseDuration) {
        public boolean shouldPauseProducer() {
            return throttleProducer && pauseDuration.toMillis() > 0;
        }
    }

    private record MemorySnapshot(long usedBytes, long maxBytes) {
        double usagePercent() {
            if (maxBytes <= 0) {
                return 0d;
            }
            return (usedBytes * 100d) / maxBytes;
        }
    }
}
