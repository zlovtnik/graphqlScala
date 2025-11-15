# Metrics Reference Guide

Complete reference of all Prometheus metrics exported by the GraphQL optimization infrastructure.

## GraphQL Metrics

### Persisted Queries

`graphql.persisted_queries.cache_hits_total`
- Type: Counter
- Description: Total number of persisted query cache hits
- Labels: `client_name`, `query_type`

`graphql.persisted_queries.cache_misses_total`
- Type: Counter
- Description: Total cache misses (not found in registry)
- Labels: `client_name`, `query_type`

`graphql.persisted_queries.registered_total`
- Type: Counter
- Description: Total queries registered in persisted query registry
- Labels: None

`graphql.persisted_queries.registration_errors_total`
- Type: Counter
- Description: Failed registrations
- Labels: `error_type` (complexity_exceeded, hash_collision, etc)

`graphql.persisted_queries.registration_duration_ms`
- Type: Histogram (p50, p95, p99)
- Description: Time to register a new persisted query
- Labels: None

`graphql.persisted_queries.complexity_score`
- Type: Gauge (histogram with percentiles)
- Description: Query complexity score distribution
- Labels: `percentile` (p50, p95, p99)

## HTTP Metrics

### Compression

`http.response.compression.selected`
- Type: Counter
- Description: Responses with compression applied
- Labels: `algorithm` (gzip, br)

`http.compression.filter.duration_ms`
- Type: Histogram
- Description: Time spent in compression filter
- Labels: `endpoint` (truncated path)

### Caching

`http.cache.hit`
- Type: Counter
- Description: 304 Not Modified responses (cache validation hits)
- Labels: None

`http.cache.miss`
- Type: Counter
- Description: 200 OK full responses sent
- Labels: None

`http.cache.etag.generated`
- Type: Counter
- Description: ETags generated
- Labels: None

`http.cache.max_age`
- Type: Gauge
- Description: Cache-Control max-age value
- Labels: `content_type`

## Query Performance Metrics

### Query Execution

`graphql.query.execution_time_ms`
- Type: Histogram
- Description: Query execution time distribution
- Labels: `query_type` (SELECT, INSERT, UPDATE, DELETE)
- Percentiles: p50, p95, p99

`graphql.query.count`
- Type: Counter
- Description: Total queries executed
- Labels: `query_type`

`graphql.query.avg_time_ms`
- Type: Gauge
- Description: Average execution time
- Labels: `query_type`

`graphql.query.max_time_ms`
- Type: Gauge
- Description: Maximum execution time
- Labels: `query_type`

### Query Analysis

`graphql.query.n_plus_one_detected`
- Type: Counter
- Description: N+1 query patterns detected
- Labels: `query_type`, `count` (number of duplicates)

`graphql.query.anomaly.detected`
- Type: Counter
- Description: Query execution anomalies (>5% variance from baseline)
- Labels: `query_type`, `variance` (percentage)

`graphql.query.errors`
- Type: Counter
- Description: Query execution errors
- Labels: `error_type`

## Audit Metrics

### GraphQL Complexity Audits

`audit.graphql_complexity_total`
- Type: Counter
- Description: Complexity audits recorded
- Labels: None

`audit.graphql_complexity.errors_total`
- Type: Counter
- Description: Failed complexity audit logging
- Labels: None

`audit.graphql_complexity.duration_ms`
- Type: Histogram
- Description: Time to record complexity audit
- Labels: None
- Percentiles: p50, p95, p99

### Circuit Breaker Audits

`audit.circuit_breaker_events_total`
- Type: Counter
- Description: Circuit breaker state transitions logged
- Labels: `service` (database, redis, minio, auth-service, audit-service)

`audit.circuit_breaker.errors_total`
- Type: Counter
- Description: Failed circuit breaker event logging
- Labels: None

### Compression Audits

`audit.http_compression_total`
- Type: Counter
- Description: Compression events logged
- Labels: `algorithm` (gzip, br)

`audit.http_compression.errors_total`
- Type: Counter
- Description: Failed compression audit logging
- Labels: None

`audit.http_compression.ratio`
- Type: Gauge
- Description: Compression ratio (compressed_size / original_size)
- Labels: None

### Execution Plan Audits

`audit.execution_plan_total`
- Type: Counter
- Description: Execution plans sampled and audited (1 in 100)
- Labels: None

`audit.execution_plan.not_sampled_total`
- Type: Counter
- Description: Execution plans not sampled (99 in 100)
- Labels: None

## Resilience Metrics

### Circuit Breaker State

`resilience4j_circuitbreaker_state`
- Type: Gauge
- Description: Circuit breaker state (0=CLOSED, 1=OPEN, 2=HALF_OPEN)
- Labels: `name` (database, redis, minio, auth-service, audit-service)

`resilience4j_circuitbreaker_calls_total`
- Type: Counter
- Description: Circuit breaker calls
- Labels: `name`, `kind` (successful, failed)

`resilience4j_circuitbreaker_slow_calls_total`
- Type: Counter
- Description: Calls exceeding slow threshold
- Labels: `name`

`resilience4j_circuitbreaker_slow_calls_duration_ms`
- Type: Histogram
- Description: Slow call duration distribution
- Labels: `name`
- Percentiles: p50, p95, p99

### Retry Metrics

`resilience4j_retry_calls_total`
- Type: Counter
- Description: Retry call attempts
- Labels: `name`, `kind` (successful, failed)

`resilience4j_retry_attempts_total`
- Type: Counter
- Description: Total retry attempts
- Labels: `name`

## Database Metrics

### R2DBC Connection Pool

`r2dbc.pool.acquired`
- Type: Gauge
- Description: Active connections in use
- Labels: None

`r2dbc.pool.idle`
- Type: Gauge
- Description: Idle connections waiting
- Labels: None

`r2dbc.pool.pending`
- Type: Gauge
- Description: Pending connection requests in queue
- Labels: None

`r2dbc.connection.creation.time`
- Type: Histogram
- Description: Connection establishment latency
- Labels: None
- Percentiles: p50, p95, p99

## Custom Metrics

### Cache Performance

`caffeine.cache.hits`
- Type: Counter
- Description: Caffeine L1 cache hits
- Labels: `cache_name`

`caffeine.cache.misses`
- Type: Counter
- Description: Caffeine L1 cache misses
- Labels: `cache_name`

`redis.cache.hits`
- Type: Counter
- Description: Redis L2 cache hits
- Labels: `cache_name`

`redis.cache.misses`
- Type: Counter
- Description: Redis L2 cache misses
- Labels: `cache_name`

### Service Health

`service.health.database`
- Type: Gauge
- Description: Database health (0=down, 1=up)

`service.health.redis`
- Type: Gauge
- Description: Redis health status

`service.health.minio`
- Type: Gauge
- Description: MinIO health status

## Prometheus Scrape Configuration

```yaml
# prometheus.yml
global:
  scrape_interval: 15s
  evaluation_interval: 15s

scrape_configs:
  - job_name: 'graphql-api'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['localhost:8443']
    scheme: https
    tls_config:
      insecure_skip_verify: true
```

## Grafana Dashboard Variables

```json
{
  "variables": [
    {
      "name": "service",
      "query": "label_values(resilience4j_circuitbreaker_state, name)",
      "type": "query"
    },
    {
      "name": "query_type",
      "query": "label_values(graphql_query_execution_time_ms, query_type)",
      "type": "query"
    },
    {
      "name": "algorithm",
      "query": "label_values(http_response_compression_selected, algorithm)",
      "type": "query"
    }
  ]
}
```

## Alerting Thresholds

| Metric | Warning | Critical |
|--------|---------|----------|
| Cache Hit Ratio | <30% | <10% |
| Circuit Breaker OPEN | Any | >5 minutes |
| Query P99 Latency | >2000ms | >5000ms |
| N+1 Query Count | >10/min | >50/min |
| Anomaly Detection Rate | >1% | >5% |
| Compression Ratio | <0.3 | <0.2 |
| Connection Pool Pending | >100 | >500 |
| Auth Service Latency P99 | >200ms | >500ms |

## Related

- [Metrics Dashboard Configs](../dashboards/)
- [Alerting Rules](../alerts/)
- [Troubleshooting](troubleshooting.md)
