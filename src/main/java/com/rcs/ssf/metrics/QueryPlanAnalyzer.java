package com.rcs.ssf.metrics;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Query Plan Analysis and Anomaly Detection.
 * 
 * Features:
 * - Tracks execution time of individual SQL queries
 * - Detects N+1 query patterns
 * - Identifies slow queries (top 10)
 * - Implements anomaly detection: alert if execution >5% variance from baseline
 * - Calculates P50/P95/P99 latency percentiles
 * - Exports metrics to Prometheus
 * 
 * Instrumentation Points:
 * - Spring Data JDBC execute() method
 * - Custom @LogQueryExecution interceptor
 * - R2DBC statement execution listeners
 * 
 * Metrics:
 * - graphql.query.execution_time_ms (histogram with percentiles)
 * - graphql.query.count (counter, tagged by query type)
 * - graphql.query.errors (counter)
 * - graphql.query.n_plus_one_detected (counter)
 * - graphql.query.anomaly.detected (counter)
 * 
 * Usage:
 * 
 * @Component annotation auto-loads this as a Spring bean.
 *            Integrate with JDBC interceptor or R2DBC listener for execution
 *            hooks.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class QueryPlanAnalyzer {

    private final MeterRegistry meterRegistry;

    private static final int BASELINE_SAMPLES = 100;
    private static final double ANOMALY_THRESHOLD_PERCENT = 5.0;
    private static final int TOP_QUERIES_LIMIT = 10;
    private static final int QUERY_METRICS_MAX_ENTRIES = 10_000;
    private static final long N_PLUS_ONE_THRESHOLD_MS = 100; // Similar queries within 100ms
    private static final int N_PLUS_ONE_MIN_COUNT = 3;

    // Thread-safe collections for tracking queries
    private final Map<String, Double> queryBaselines = new ConcurrentHashMap<>();
    private final Cache<String, QueryStats> queryMetrics = Caffeine.newBuilder()
            .maximumSize(QUERY_METRICS_MAX_ENTRIES)
            .removalListener((String key, QueryStats stats, RemovalCause cause) -> {
                if (key != null) {
                    queryBaselines.remove(key);
                }
            })
            .build();
    private final Deque<QueryExecution> recentQueries = new ArrayDeque<>(); // Last 1000 queries

    /**
     * Record a query execution for analysis.
     * 
     * @param query           SQL query string
     * @param executionTimeMs Execution time in milliseconds
     * @param rowsAffected    Number of rows returned/affected
     */
    public void recordExecution(String query, long executionTimeMs, long rowsAffected) {
        String queryType = normalizeQuery(query);

        // Update statistics
        QueryStats stats = queryMetrics.get(queryType, k -> new QueryStats(query));
        stats.recordExecution(executionTimeMs, rowsAffected);

        // Track recent queries for N+1 detection
        synchronized (recentQueries) {
            recentQueries.addLast(new QueryExecution(queryType, System.currentTimeMillis()));
            if (recentQueries.size() > 1000) {
                recentQueries.removeFirst();
            }
        }

        // Export metrics
        exportMetrics(queryType, executionTimeMs);

        // Check for anomalies
        checkForAnomalies(queryType, executionTimeMs);

        // Check for N+1 queries
        if (recentQueries.size() >= N_PLUS_ONE_MIN_COUNT) {
            detectNPlusOne();
        }
    }

    /**
     * Export metrics to Prometheus.
     */
    private void exportMetrics(String queryType, long executionTimeMs) {
        QueryStats stats = queryMetrics.getIfPresent(queryType);
        if (stats == null)
            return;

        // Histogram with percentiles
        Timer.builder("graphql.query.execution_time_ms")
                .tag("query_type", extractType(queryType))
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(meterRegistry)
                .record(executionTimeMs, java.util.concurrent.TimeUnit.MILLISECONDS);

        // Total count
        meterRegistry.counter("graphql.query.count",
                Tags.of("query_type", extractType(queryType))).increment();

        // Average execution time
        meterRegistry.gauge("graphql.query.avg_time_ms",
                Tags.of("query_type", extractType(queryType)),
                stats.getAverageExecutionTime());

        // Max execution time
        meterRegistry.gauge("graphql.query.max_time_ms",
                Tags.of("query_type", extractType(queryType)),
                stats.getMaxExecutionTime());
    }

    /**
     * Detect anomalies in query execution time.
     * 
     * Trigger alert if execution time >5% variance from baseline.
     */
    private void checkForAnomalies(String queryType, long executionTimeMs) {
        QueryStats stats = queryMetrics.getIfPresent(queryType);
        if (stats == null || stats.getCount() < BASELINE_SAMPLES) {
            // Not enough samples yet
            if (stats != null && stats.getCount() == BASELINE_SAMPLES) {
                // Just reached baseline
                double baseline = stats.getAverageExecutionTime();
                queryBaselines.put(queryType, baseline);
                log.info("Query baseline established: {} = {}ms", queryType, baseline);
            }
            return;
        }

        Double baseline = queryBaselines.getOrDefault(queryType, stats.getAverageExecutionTime());
        double variance = Math.abs((executionTimeMs - baseline) / baseline) * 100;

        if (variance > ANOMALY_THRESHOLD_PERCENT) {
            meterRegistry.counter("graphql.query.anomaly.detected",
                    Tags.of("query_type", extractType(queryType), "variance", String.format("%.1f%%", variance)))
                    .increment();

            log.warn("Anomaly detected: {} took {}ms (baseline: {}ms, variance: {:.1f}%)",
                    queryType, executionTimeMs, baseline, variance);
        }
    }

    /**
     * Detect N+1 query patterns.
     * 
     * Triggers when same/similar query executed N+ times in quick succession.
     * Clusters queries by proximity to each other per query type.
     */
    private void detectNPlusOne() {
        synchronized (recentQueries) {
            if (recentQueries.size() < N_PLUS_ONE_MIN_COUNT) {
                return;
            }

            // Group queries by type
            Map<String, List<QueryExecution>> queriesByType = new HashMap<>();
            for (QueryExecution qe : recentQueries) {
                queriesByType.computeIfAbsent(qe.queryType, k -> new ArrayList<>()).add(qe);
            }

            // For each query type, cluster by temporal proximity
            for (Map.Entry<String, List<QueryExecution>> entry : queriesByType.entrySet()) {
                String queryType = entry.getKey();
                List<QueryExecution> queries = entry.getValue();

                if (queries.size() < N_PLUS_ONE_MIN_COUNT) {
                    continue;
                }

                // Sort by timestamp
                queries.sort(Comparator.comparingLong(q -> q.timestamp));

                // Find clusters where consecutive queries are within threshold
                int clusterSize = 1;
                long clusterStart = queries.get(0).timestamp;
                long clusterEnd = clusterStart;

                for (int i = 1; i < queries.size(); i++) {
                    long currentTimestamp = queries.get(i).timestamp;

                    if (currentTimestamp - clusterEnd <= N_PLUS_ONE_THRESHOLD_MS) {
                        // Extend current cluster
                        clusterEnd = currentTimestamp;
                        clusterSize++;
                    } else {
                        // Check if previous cluster meets threshold
                        if (clusterSize >= N_PLUS_ONE_MIN_COUNT) {
                            reportNPlusOne(queryType, clusterSize, clusterEnd - clusterStart);
                        }

                        // Start new cluster
                        clusterStart = currentTimestamp;
                        clusterEnd = currentTimestamp;
                        clusterSize = 1;
                    }
                }

                // Check final cluster
                if (clusterSize >= N_PLUS_ONE_MIN_COUNT) {
                    reportNPlusOne(queryType, clusterSize, clusterEnd - clusterStart);
                }
            }
        }
    }

    private void reportNPlusOne(String queryType, int count, long durationMs) {
        meterRegistry.counter("graphql.query.n_plus_one_detected",
                Tags.of("query_type", extractType(queryType)))
                .increment();

        log.warn("N+1 Query Pattern Detected: {} executed {} times in {} ms cluster",
                queryType, count, durationMs);
    }

    /**
     * Get top 10 slowest queries.
     * 
     * @return List of slowest queries with execution times
     */
    public List<String> getTopSlowQueries() {
        return queryMetrics.asMap().entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue().getMaxExecutionTime(), a.getValue().getMaxExecutionTime()))
                .limit(TOP_QUERIES_LIMIT)
                .map(e -> String.format("%s (max: %d ms, avg: %.1f ms, count: %d)",
                        e.getValue().getOriginalQuery(),
                        e.getValue().getMaxExecutionTime(),
                        e.getValue().getAverageExecutionTime(),
                        e.getValue().getCount()))
                .collect(Collectors.toList());
    }

    /**
     * Get resolver execution breakdown (P50/P95/P99).
     * 
     * @return Map of resolver names to latency statistics
     */
    public Map<String, ResolverStats> getResolverBreakdown() {
        Map<String, ResolverStats> stats = new HashMap<>();

        queryMetrics.asMap().forEach((queryType, queryStats) -> {
            String resolver = extractResolverName(queryType);
            stats.computeIfAbsent(resolver, k -> new ResolverStats())
                    .addQuery(queryStats);
        });

        return stats;
    }

    /**
     * Normalize query by removing values to find similar queries.
     */
    private String normalizeQuery(String query) {
        if (query == null)
            return "unknown";

        // Replace parameter values with placeholders
        String normalized = query.replaceAll("'[^']*'", "?") // String values
                .replaceAll("\\b\\d+\\b", "?") // Numeric values
                .replaceAll("\\s+", " ") // Normalize whitespace
                .toLowerCase(java.util.Locale.ROOT);

        return normalized.substring(0, Math.min(200, normalized.length())); // Truncate for key
    }

    private String extractType(String queryType) {
        if (queryType == null)
            return "OTHER";
        String normalized = queryType.toLowerCase(java.util.Locale.ROOT);
        if (normalized.contains("select"))
            return "SELECT";
        if (normalized.contains("insert"))
            return "INSERT";
        if (normalized.contains("update"))
            return "UPDATE";
        if (normalized.contains("delete"))
            return "DELETE";
        return "OTHER";
    }

    private String extractResolverName(String queryType) {
        // Extract table name or resolver from query
        if (queryType == null)
            return "unknown";
        String normalized = queryType.toLowerCase(java.util.Locale.ROOT);
        if (normalized.contains("from")) {
            String[] parts = normalized.split("from");
            if (parts.length > 1) {
                return parts[1].trim().split("\\s")[0].toLowerCase(java.util.Locale.ROOT);
            }
        }
        return "unknown";
    }

    /**
     * Query statistics for tracking execution patterns.
     * 
     * Thread-safe with bounded sampling: stores at most 1000 recent execution times
     * to prevent unbounded memory growth. Uses ConcurrentLinkedDeque and atomic
     * operations
     * for concurrent updates with minimal lock contention.
     */
    public static class QueryStats {
        private static final int MAX_SAMPLES = 1000;
        private final String originalQuery;
        private final java.util.concurrent.ConcurrentLinkedDeque<Long> executionTimes = new java.util.concurrent.ConcurrentLinkedDeque<>();
        private final java.util.concurrent.atomic.AtomicLong minExecutionTime = new java.util.concurrent.atomic.AtomicLong(
                Long.MAX_VALUE);
        private final java.util.concurrent.atomic.AtomicLong maxExecutionTime = new java.util.concurrent.atomic.AtomicLong(
                0);
        private volatile boolean minMaxStale = false;
        private final java.util.concurrent.atomic.LongAdder totalExecutionTime = new java.util.concurrent.atomic.LongAdder();
        protected final Object lock = new Object();

        public QueryStats(String originalQuery) {
            this.originalQuery = originalQuery;
        }

        public void recordExecution(long executionTimeMs, long rowsAffected) {
            Long removedTime = null;
            synchronized (lock) {
                executionTimes.addLast(executionTimeMs);
                // Trim oldest samples if exceeding max
                if (executionTimes.size() > MAX_SAMPLES) {
                    removedTime = executionTimes.removeFirst();
                    handleRemovalEffects(removedTime);
                }
                // Update totals inside synchronized block to avoid race condition
                // Subtract removed sample from total
                if (removedTime != null) {
                    totalExecutionTime.add(-removedTime);
                }
                // Add new sample to total
                totalExecutionTime.add(executionTimeMs);
            }
            // Update min/max using atomic operations (non-blocking, outside lock)
            updateMinMax(executionTimeMs);
        }

        private void handleRemovalEffects(long removedValue) {
            long currentMin = minExecutionTime.get();
            long currentMax = maxExecutionTime.get();

            if (executionTimes.isEmpty()) {
                minExecutionTime.set(Long.MAX_VALUE);
                maxExecutionTime.set(0);
                minMaxStale = false;
                return;
            }

            if (removedValue == currentMin || removedValue == currentMax) {
                minMaxStale = true;
            }
        }

        private void recomputeMinMaxIfNeeded() {
            if (!minMaxStale) {
                return;
            }
            synchronized (lock) {
                if (!minMaxStale) {
                    return;
                }

                long newMin = Long.MAX_VALUE;
                long newMax = 0;
                for (long time : executionTimes) {
                    if (time < newMin) {
                        newMin = time;
                    }
                    if (time > newMax) {
                        newMax = time;
                    }
                }

                if (executionTimes.isEmpty()) {
                    minExecutionTime.set(Long.MAX_VALUE);
                    maxExecutionTime.set(0);
                } else {
                    minExecutionTime.set(newMin);
                    maxExecutionTime.set(newMax);
                }

                minMaxStale = false;
            }
        }

        private void updateMinMax(long executionTimeMs) {
            long currentMin = minExecutionTime.get();
            while (executionTimeMs < currentMin) {
                if (minExecutionTime.compareAndSet(currentMin, executionTimeMs)) {
                    break;
                }
                currentMin = minExecutionTime.get();
            }

            long currentMax = maxExecutionTime.get();
            while (executionTimeMs > currentMax) {
                if (maxExecutionTime.compareAndSet(currentMax, executionTimeMs)) {
                    break;
                }
                currentMax = maxExecutionTime.get();
            }
        }

        public long getCount() {
            return executionTimes.size();
        }

        public long getMinExecutionTime() {
            recomputeMinMaxIfNeeded();
            long min = minExecutionTime.get();
            return min == Long.MAX_VALUE ? 0 : min;
        }

        public long getMaxExecutionTime() {
            recomputeMinMaxIfNeeded();
            return maxExecutionTime.get();
        }

        public double getAverageExecutionTime() {
            long count = getCount();
            return count > 0 ? (double) totalExecutionTime.sum() / count : 0;
        }

        public String getOriginalQuery() {
            return originalQuery;
        }
    }

    /**
     * Resolver statistics for breakdown analysis with thread-safe sampling.
     * 
     * Snapshots execution times from each query's bounded deque to prevent
     * ConcurrentModificationException while computing percentiles.
     */
    public static class ResolverStats {
        private final List<QueryStats> queries = new ArrayList<>();

        public void addQuery(QueryStats stats) {
            queries.add(stats);
        }

        public double getP50() {
            return getPercentile(0.5);
        }

        public double getP95() {
            return getPercentile(0.95);
        }

        public double getP99() {
            return getPercentile(0.99);
        }

        private double getPercentile(double percentile) {
            // Snapshot all times from all queries' bounded deques to avoid
            // ConcurrentModificationException
            List<Long> times = queries.stream()
                    .flatMap(q -> {
                        synchronized (q.lock) {
                            return new ArrayList<>(q.executionTimes).stream();
                        }
                    })
                    .sorted()
                    .toList();
            if (times.isEmpty())
                return 0;
            int index = (int) (times.size() * percentile);
            return times.get(Math.min(index, times.size() - 1));
        }

        public long getTotalQueries() {
            return queries.stream().mapToLong(QueryStats::getCount).sum();
        }
    }

    /**
     * Recent query execution for N+1 detection.
     */
    private static class QueryExecution {
        final String queryType;
        final long timestamp;

        QueryExecution(String queryType, long timestamp) {
            this.queryType = queryType;
            this.timestamp = timestamp;
        }
    }
}
