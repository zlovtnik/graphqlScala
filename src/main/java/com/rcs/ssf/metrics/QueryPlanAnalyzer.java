package com.rcs.ssf.metrics;

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
 * @Component annotation auto-loads this as a Spring bean.
 * Integrate with JDBC interceptor or R2DBC listener for execution hooks.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class QueryPlanAnalyzer {

    private final MeterRegistry meterRegistry;

    private static final int BASELINE_SAMPLES = 100;
    private static final double ANOMALY_THRESHOLD_PERCENT = 5.0;
    private static final int TOP_QUERIES_LIMIT = 10;
    private static final long N_PLUS_ONE_THRESHOLD_MS = 100; // Similar queries within 100ms
    private static final int N_PLUS_ONE_MIN_COUNT = 3;

    // Thread-safe collections for tracking queries
    private final Map<String, QueryStats> queryMetrics = new ConcurrentHashMap<>();
    private final Deque<QueryExecution> recentQueries = new ArrayDeque<>(); // Last 1000 queries
    private final Map<String, Double> queryBaselines = new ConcurrentHashMap<>();

    /**
     * Record a query execution for analysis.
     * 
     * @param query SQL query string
     * @param executionTimeMs Execution time in milliseconds
     * @param rowsAffected Number of rows returned/affected
     */
    public void recordExecution(String query, long executionTimeMs, long rowsAffected) {
        String queryType = normalizeQuery(query);
        
        // Update statistics
        QueryStats stats = queryMetrics.computeIfAbsent(queryType, k -> new QueryStats(query));
        stats.recordExecution(executionTimeMs, rowsAffected);
        
        // Track recent queries for N+1 detection
        synchronized (recentQueries) {
            recentQueries.addLast(new QueryExecution(queryType, executionTimeMs, System.currentTimeMillis()));
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
        QueryStats stats = queryMetrics.get(queryType);
        if (stats == null) return;

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
        QueryStats stats = queryMetrics.get(queryType);
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
     */
    private void detectNPlusOne() {
        synchronized (recentQueries) {
            if (recentQueries.size() < N_PLUS_ONE_MIN_COUNT) {
                return;
            }

            // Group recent queries by type and timestamp proximity
            Map<String, List<QueryExecution>> grouped = new HashMap<>();
            long now = System.currentTimeMillis();

            for (QueryExecution qe : recentQueries) {
                if (now - qe.timestamp < N_PLUS_ONE_THRESHOLD_MS) {
                    grouped.computeIfAbsent(qe.queryType, k -> new ArrayList<>()).add(qe);
                }
            }

            // Check for N+1 pattern
            for (Map.Entry<String, List<QueryExecution>> entry : grouped.entrySet()) {
                if (entry.getValue().size() >= N_PLUS_ONE_MIN_COUNT) {
                    String queryType = entry.getKey();
                    int count = entry.getValue().size();

                    meterRegistry.counter("graphql.query.n_plus_one_detected",
                            Tags.of("query_type", extractType(queryType), "count", String.valueOf(count)))
                            .increment();

                    log.warn("N+1 Query Pattern Detected: {} executed {} times in {} ms",
                            queryType, count, N_PLUS_ONE_THRESHOLD_MS);
                }
            }
        }
    }

    /**
     * Get top 10 slowest queries.
     * 
     * @return List of slowest queries with execution times
     */
    public List<String> getTopSlowQueries() {
        return queryMetrics.entrySet().stream()
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
        
        queryMetrics.forEach((queryType, queryStats) -> {
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
        if (query == null) return "unknown";
        
        // Replace parameter values with placeholders
        return query.replaceAll("'[^']*'", "?")  // String values
                .replaceAll("\\b\\d+\\b", "?")  // Numeric values
                .replaceAll("\\s+", " ")         // Normalize whitespace
                .toLowerCase()
                .substring(0, Math.min(200, query.length())); // Truncate for key
    }

    private String extractType(String queryType) {
        if (queryType.contains("SELECT")) return "SELECT";
        if (queryType.contains("INSERT")) return "INSERT";
        if (queryType.contains("UPDATE")) return "UPDATE";
        if (queryType.contains("DELETE")) return "DELETE";
        return "OTHER";
    }

    private String extractResolverName(String queryType) {
        // Extract table name or resolver from query
        if (queryType.contains("FROM")) {
            String[] parts = queryType.split("FROM");
            if (parts.length > 1) {
                return parts[1].trim().split("\\s")[0].toLowerCase();
            }
        }
        return "unknown";
    }

    /**
     * Query statistics for tracking execution patterns.
     */
    public static class QueryStats {
        private final String originalQuery;
        private final List<Long> executionTimes = new ArrayList<>();
        private long minExecutionTime = Long.MAX_VALUE;
        private long maxExecutionTime = 0;
        private long totalExecutionTime = 0;

        public QueryStats(String originalQuery) {
            this.originalQuery = originalQuery;
        }

        public void recordExecution(long executionTimeMs, long rowsAffected) {
            executionTimes.add(executionTimeMs);
            minExecutionTime = Math.min(minExecutionTime, executionTimeMs);
            maxExecutionTime = Math.max(maxExecutionTime, executionTimeMs);
            totalExecutionTime += executionTimeMs;
        }

        public long getCount() { return executionTimes.size(); }
        public long getMinExecutionTime() { return minExecutionTime; }
        public long getMaxExecutionTime() { return maxExecutionTime; }
        public double getAverageExecutionTime() {
            return getCount() > 0 ? (double) totalExecutionTime / getCount() : 0;
        }
        public String getOriginalQuery() { return originalQuery; }
    }

    /**
     * Resolver statistics for breakdown analysis.
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
            List<Long> times = queries.stream()
                    .flatMap(q -> q.executionTimes.stream())
                    .sorted()
                    .toList();
            if (times.isEmpty()) return 0;
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
        String queryType;
        long executionTime;
        long timestamp;

        QueryExecution(String queryType, long executionTime, long timestamp) {
            this.queryType = queryType;
            this.executionTime = executionTime;
            this.timestamp = timestamp;
        }
    }
}
